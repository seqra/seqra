import argparse
import copy
import json
import logging
import re
import subprocess
from collections import defaultdict
from pathlib import Path

import yaml

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)


def load_template(template_path: Path) -> dict:
    """Load and validate ZAP automation template"""
    with open(template_path) as f:
        template = yaml.safe_load(f)
    if "env" not in template or "contexts" not in template["env"]:
        raise ValueError("Template missing required 'env.contexts' section")
    if "jobs" not in template:
        raise ValueError("Template missing required 'jobs' section")
    return template


def validate_template(template: dict) -> None:
    """Validate required template fields, raise error if missing"""
    has_openapi = False
    has_graphql = False
    for job in template["jobs"]:
        job_type = job.get("type", "")
        if job_type == "openapi":
            has_openapi = True
        elif job_type == "graphql":
            has_graphql = True
    if not has_openapi and not has_graphql:
        raise ValueError("Template must contain at least one 'openapi' or 'graphql' job")
    if not template["env"]["contexts"]:
        raise ValueError("Template must contain at least one context in 'env.contexts'")

    cwe_policies = []
    for job in template["jobs"]:
        if job.get("type") == "activeScan-policy":
            policy_name = job.get("parameters", {}).get("name", "")
            if policy_name.startswith("policy-CWE-"):
                cwe_policies.append(policy_name)

    if not cwe_policies:
        raise ValueError("Template must contain at least one policy with format 'policy-CWE-X'")
    logger.info("Template validation passed")


def ensure_report_jobs(template: dict) -> dict:
    """Ensure template has required report job and normalize all report directories"""
    required_dir = "/zap/wrk/zap-output"
    required_report = {
        "type": "report",
        "parameters": {
            "template": "traditional-json",
            "reportDir": required_dir,
            "reportFile": "opentaint_zap_scan_results",
            "reportTitle": "OpenTaint + ZAP Scan Report",
            "reportDescription": "Automated security scan results for filtering sarif",
        },
        "risks": ["high", "medium"],
        "confidences": ["high", "medium", "low"],
    }

    has_json_report = False
    for job in template["jobs"]:
        if job.get("type") == "report":
            params = job.get("parameters", {})
            if (
                params.get("template") == "traditional-json"
                and params.get("reportFile") == "opentaint_zap_scan_results"
            ):
                has_json_report = True
                logger.info("Found required traditional-json report job")

            current_dir = params.get("reportDir", "")
            if current_dir != required_dir and not current_dir.startswith(required_dir + "/"):
                logger.warning(
                    f"Report job with template '{params.get('template', 'unknown')}' has reportDir='{current_dir}'. "
                    f"Replacing with '{required_dir}'"
                )
                params["reportDir"] = required_dir

    # Add required JSON report if missing
    if not has_json_report:
        logger.warning("Template missing required traditional-json report job, adding automatically")
        template["jobs"].append(required_report)
        logger.info("Added traditional-json report job with reportFile='opentaint_zap_scan_results'")

    return template


def check_template_warnings(template: dict) -> None:
    """Check template for potential issues and show warnings"""
    contexts = template["env"]["contexts"]
    if len(contexts) > 1:
        logger.warning(
            f"Template contains {len(contexts)} contexts. Will use first context by default. "
            "Use --context-name to specify a different context."
        )
    non_cwe_policies = []
    for job in template["jobs"]:
        if job.get("type") == "activeScan-policy":
            policy_name = job.get("parameters", {}).get("name", "")
            if not policy_name.startswith("policy-CWE-"):
                non_cwe_policies.append(policy_name)
    if non_cwe_policies:
        logger.warning(
            f"Template contains {len(non_cwe_policies)} non-CWE policies that will be kept in output: "
            f"{', '.join(non_cwe_policies)}"
        )
    existing_scans = []
    for job in template["jobs"]:
        if job.get("type") == "activeScan":
            ctx = job.get("parameters", {}).get("context", "unknown")
            existing_scans.append(ctx)
    if existing_scans:
        logger.warning(
            f"Template contains {len(existing_scans)} existing activeScan jobs that will be removed: "
            f"{', '.join(existing_scans)}"
        )


def check_policy_rules(template: dict) -> None:
    """Warn if CWE policies have no rules defined"""
    for job in template["jobs"]:
        if job.get("type") == "activeScan-policy":
            policy_name = job.get("parameters", {}).get("name", "")
            if policy_name.startswith("policy-CWE-"):
                policy_def = job.get("policyDefinition", {})
                rules = policy_def.get("rules", [])
                if not rules:
                    logger.warning(f"Policy '{policy_name}' has no rules defined")


def select_context(template: dict, context_name: str | None) -> tuple[dict, int]:
    """Select context by name or use first one"""
    contexts = template["env"]["contexts"]
    if context_name:
        for idx, ctx in enumerate(contexts):
            if ctx.get("name") == context_name:
                logger.info(f"Using context: {context_name}")
                if len(contexts) > 1:
                    logger.warning(f"Other {len(contexts) - 1} context(s) will be ignored")
                return ctx, idx
        raise ValueError(f"Context '{context_name}' not found in template")
    logger.info(f"Using first context: {contexts[0].get('name', 'unnamed')}")
    return contexts[0], 0


def extract_path_filters(context: dict) -> dict:
    """Extract and compile path filter patterns from context"""
    filters = {"urls": [], "include": [], "exclude": []}
    for url in context.get("urls", []):
        pattern = re.escape(url) + ".*"
        filters["urls"].append(re.compile(pattern))
    for pattern in context.get("includePaths", []):
        try:
            filters["include"].append(re.compile(pattern))
        except re.error as e:
            logger.warning(f"Invalid includePath regex '{pattern}': {e}")
    for pattern in context.get("excludePaths", []):
        try:
            filters["exclude"].append(re.compile(pattern))
        except re.error as e:
            logger.warning(f"Invalid excludePath regex '{pattern}': {e}")
    return filters


def should_include_path(path: str, filters: dict, target_url: str) -> bool:
    """Check if path should be included based on context filters"""
    full_path = f"{target_url}{path}"
    if filters["urls"] and not any(pattern.match(full_path) for pattern in filters["urls"]):
        return False

    if filters["include"] and not any(pattern.match(full_path) for pattern in filters["include"]):
        return False

    return not (filters["exclude"] and any(pattern.match(full_path) for pattern in filters["exclude"]))


def filter_cwe_paths(cwe_paths: dict[int, list[str]], filters: dict, target_url: str) -> dict[int, list[str]]:
    """Apply path filters to CWE paths mapping"""
    filtered = {}
    total_before = sum(len(paths) for paths in cwe_paths.values())
    total_after = 0
    for cwe_num, paths in cwe_paths.items():
        filtered_paths = [p for p in paths if should_include_path(p, filters, target_url)]
        if filtered_paths:
            filtered[cwe_num] = filtered_paths
            total_after += len(filtered_paths)
            if len(filtered_paths) < len(paths):
                logger.debug(f"CWE-{cwe_num}: filtered {len(paths)} -> {len(filtered_paths)} paths")
    if total_before > total_after:
        logger.info(
            f"Path filtering: {total_before} -> {total_after} paths ({total_before - total_after} filtered out)"
        )
    return filtered


def extract_base_context_config(context: dict) -> dict:
    """Extract inheritable config from context"""
    config = {}
    for key in ["authentication", "sessionManagement", "technology", "structure", "users"]:
        if key in context:
            config[key] = context[key]
    return config


def extract_cwe_policies(template: dict) -> dict[int, str]:
    """Extract CWE number to policy name mapping from template"""
    policies = {}
    for job in template["jobs"]:
        if job.get("type") == "activeScan-policy":
            policy_name = job["parameters"]["name"]
            if policy_name.startswith("policy-CWE-"):
                cwe_num = int(policy_name.replace("policy-CWE-", ""))
                policies[cwe_num] = policy_name

    logger.info(f"Found {len(policies)} CWE policies in template")
    return policies


def report_policy_coverage(policies: dict[int, str], cwe_paths: dict[int, list[str]]) -> None:
    """Report which policies have matching SARIF paths"""
    for cwe_num, policy_name in sorted(policies.items()):
        if cwe_num in cwe_paths:
            logger.info(f"{policy_name}: {len(cwe_paths[cwe_num])} paths found in SARIF")
        else:
            logger.info(f"{policy_name}: no paths found in SARIF")


def parse_sarif_for_cwe_paths(sarif_path: Path, available_cwes: set[int]) -> dict[int, list[str]]:
    """Parse SARIF and return CWE to paths mapping"""
    with open(sarif_path) as f:
        data = json.load(f)

    rule_metadata = {}
    for rule in data["runs"][0]["tool"]["driver"]["rules"]:
        rule_cwes = set()
        for tag in rule["properties"]["tags"]:
            if tag.startswith("CWE-"):
                cwe_num = int(tag.replace("CWE-", ""))
                if cwe_num in available_cwes:
                    rule_cwes.add(cwe_num)

        if rule_cwes:
            rule_metadata[rule["id"]] = rule_cwes

    cwe_paths = defaultdict(list)
    for result in data["runs"][0]["results"]:
        rule_id = result["ruleId"]
        if rule_id not in rule_metadata:
            continue

        cwes = rule_metadata[rule_id]

        for related in result.get("relatedLocations", []):
            for loc in related.get("logicalLocations", []):
                fqn = loc.get("fullyQualifiedName", "")
                if " " not in fqn:
                    continue

                _, path = fqn.split(" ", 1)
                for cwe in cwes:
                    if path not in cwe_paths[cwe]:
                        cwe_paths[cwe].append(path)

    logger.info(f"Parsed {len(cwe_paths)} CWE categories from SARIF")
    return dict(cwe_paths)


def create_cwe_context(cwe_num: int, paths: list[str], target_url: str, base_config: dict) -> dict:
    """Create context definition for a CWE with inherited config"""
    context = {
        "name": f"context-CWE-{cwe_num}",
        "urls": [f"{target_url}/NONEXISTENT"],
        "includePaths": [f"{target_url}{path}$" for path in paths],
        "excludePaths": [],
    }

    context.update(base_config)
    return context


def create_activescan_job(cwe_num: int, policy_name: str) -> dict:
    """Create activeScan job for CWE context"""
    return {"type": "activeScan", "parameters": {"context": f"context-CWE-{cwe_num}", "policy": policy_name}}


def run_opentaint_scan(project_path: Path) -> Path:
    """Run OpenTaint scan and return SARIF path"""
    project_name = project_path.name
    output_sarif = Path(f"scan-results/{project_name}.sarif")
    output_sarif = output_sarif.resolve()
    logger.info(f"Running OpenTaint scan on: {project_path}")
    output_sarif.parent.mkdir(parents=True, exist_ok=True)
    cmd = ["opentaint", "scan", "--output", str(output_sarif), str(project_path.resolve())]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        logger.debug(f"OpenTaint output:\n{result.stdout}")
        if not output_sarif.exists():
            raise RuntimeError(f"OpenTaint scan completed but SARIF file not found at: {output_sarif}")
        logger.info(f"OpenTaint scan completed: {output_sarif}")
        return output_sarif
    except subprocess.CalledProcessError as e:
        logger.error(f"OpenTaint scan failed with exit code {e.returncode}")
        if e.stderr:
            logger.error(f"stderr: {e.stderr}")
        raise RuntimeError(f"opentaint scan failed with exit code {e.returncode}") from e
    except FileNotFoundError as e:
        raise RuntimeError("opentaint command not found. Make sure opentaint is installed and in PATH") from e


def filter_sarif_by_base(base_sarif_path: Path, new_sarif_path: Path) -> Path:
    """Filter new SARIF to only include results not in base SARIF"""
    logger.info(f"Filtering SARIF against base: {base_sarif_path}")
    with open(base_sarif_path) as f:
        base_sarif = json.load(f)
    with open(new_sarif_path) as f:
        new_sarif = json.load(f)

    base_hashes = set()
    for result in base_sarif["runs"][0]["results"]:
        base_hashes.add(result["partialFingerprints"]["vulnerabilityWithTraceHash/v1"])
    filtered_sarif = copy.deepcopy(new_sarif)
    filtered_results = []
    for result in new_sarif["runs"][0]["results"]:
        hash_value = result["partialFingerprints"]["vulnerabilityWithTraceHash/v1"]
        if hash_value not in base_hashes:
            filtered_results.append(result)
    filtered_sarif["runs"][0]["results"] = filtered_results
    output_path = new_sarif_path.parent / f"filtered-{new_sarif_path.name}"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        json.dump(filtered_sarif, f, indent=2)
    logger.info(f"Filtered SARIF: {len(new_sarif['runs'][0]['results'])} -> {len(filtered_results)} results")
    logger.info(f"Saved filtered SARIF to: {output_path}")
    return output_path


def generate_automation_yaml(
    template_path: Path, sarif_path: Path, output_path: Path, target_url: str, context_name: str | None
) -> None:
    """Generate ZAP automation YAML from template and SARIF"""
    logger.info(f"Loading template: {template_path}")
    template = load_template(template_path)
    validate_template(template)
    template = ensure_report_jobs(template)
    check_template_warnings(template)
    check_policy_rules(template)
    selected_context, _ = select_context(template, context_name)
    base_config = extract_base_context_config(selected_context)
    path_filters = extract_path_filters(selected_context)
    policies = extract_cwe_policies(template)
    available_cwes = set(policies.keys())
    logger.info(f"Parsing SARIF: {sarif_path}")
    cwe_paths = parse_sarif_for_cwe_paths(sarif_path, available_cwes)
    report_policy_coverage(policies, cwe_paths)
    filtered_cwe_paths = filter_cwe_paths(cwe_paths, path_filters, target_url)
    if not filtered_cwe_paths:
        logger.warning("No CWE contexts will be generated (no paths matched filters or found in SARIF)")

    cwe_contexts = []
    scan_jobs = []
    for cwe_num in sorted(filtered_cwe_paths.keys()):
        if cwe_num not in policies:
            logger.warning(f"No policy found for CWE-{cwe_num}, skipping")
            continue
        paths = filtered_cwe_paths[cwe_num]
        context = create_cwe_context(cwe_num, paths, target_url, base_config)
        cwe_contexts.append(context)
        job = create_activescan_job(cwe_num, policies[cwe_num])
        scan_jobs.append(job)
        logger.debug(f"Created context and job for CWE-{cwe_num} with {len(paths)} paths")

    output_contexts = [selected_context, *cwe_contexts]
    jobs_without_activescan = [job for job in template["jobs"] if job.get("type") != "activeScan"]
    insert_idx = len(jobs_without_activescan)
    for i in range(len(jobs_without_activescan) - 1, -1, -1):
        if jobs_without_activescan[i].get("type") == "activeScan-policy":
            insert_idx = i + 1
            break

    output_jobs = jobs_without_activescan[:insert_idx] + scan_jobs + jobs_without_activescan[insert_idx:]
    output = {
        "env": {"contexts": output_contexts, **{k: v for k, v in template["env"].items() if k != "contexts"}},
        "jobs": output_jobs,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        yaml.dump(output, f, default_flow_style=False, sort_keys=False)
    logger.info(f"Generated automation YAML: {output_path}")
    logger.info(f"Total contexts: {len(output_contexts)} (1 base + {len(cwe_contexts)} CWE)")
    logger.info(f"Total jobs: {len(output_jobs)} ({len(scan_jobs)} activeScan jobs added)")


def main():
    parser = argparse.ArgumentParser(description="Generate ZAP Automation Framework YAML from SARIF")

    input_group = parser.add_mutually_exclusive_group(required=True)
    input_group.add_argument("--sarif", type=Path, help="SARIF file path")
    input_group.add_argument("--project-path", type=Path, help="Project path to scan with OpenTaint")

    parser.add_argument("--template", type=Path, required=True, help="ZAP automation template with policies")
    parser.add_argument("--target", required=True, help="Target base URL")
    parser.add_argument("--output", type=Path, default=Path("zap-automation.yaml"), help="Output YAML file path")
    parser.add_argument("--base-sarif", type=Path, help="Base SARIF for differential scanning (e.g., from main branch)")
    parser.add_argument("--context-name", help="Context name to use from template (default: first context)")
    parser.add_argument("-v", "--verbose", action="store_true", help="Enable verbose logging")

    args = parser.parse_args()
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    if not args.template.exists():
        logger.error(f"Template file not found: {args.template}")
        return 1
    sarif_path = run_opentaint_scan(args.project_path) if args.project_path else args.sarif
    if not sarif_path.exists():
        logger.error(f"SARIF file not found: {sarif_path}")
        return 1
    if args.base_sarif:
        if not args.base_sarif.exists():
            logger.error(f"Base SARIF file not found: {args.base_sarif}")
            return 1
        sarif_path = filter_sarif_by_base(args.base_sarif, sarif_path)

    try:
        generate_automation_yaml(
            template_path=args.template,
            sarif_path=sarif_path,
            output_path=args.output,
            target_url=args.target,
            context_name=args.context_name,
        )
        return 0
    except Exception as e:
        logger.error(f"Failed to generate automation YAML: {e}", exc_info=args.verbose)
        return 1


if __name__ == "__main__":
    exit(main())
