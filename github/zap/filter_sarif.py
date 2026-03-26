import argparse
import copy
import json
import logging
from pathlib import Path
from urllib.parse import urlparse

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)

TOOL_NAME = "OpenTaint + ZAP"


def extract_rule_cwe_mapping(sarif: dict) -> dict[str, int]:
    """Extract rule ID to CWE number mapping from SARIF"""
    rule_cwe = {}
    for rule in sarif["runs"][0]["tool"]["driver"]["rules"]:
        rule_id = rule["id"]
        for tag in rule["properties"]["tags"]:
            if tag.startswith("CWE-"):
                cwe_num = int(tag.replace("CWE-", ""))
                rule_cwe[rule_id] = cwe_num
                break
    return rule_cwe


def extract_confirmed_endpoints(zap_report: dict) -> set[tuple]:
    """Extract confirmed vulnerable endpoints from ZAP report"""
    confirmed = set()
    for site in zap_report.get("site", []):
        for alert in site.get("alerts", []):
            cweid = alert.get("cweid")
            if not cweid:
                continue
            cwe_num = int(cweid)
            for instance in alert.get("instances", []):
                method = instance.get("method", "")
                node_name = instance.get("nodeName", "")
                if not method or not node_name:
                    continue
                url_part = node_name.split(" ")[0] if " " in node_name else node_name
                path = urlparse(url_part).path
                confirmed.add((method, path, cwe_num))

    logger.info(f"Found {len(confirmed)} confirmed vulnerable endpoints in ZAP report")
    return confirmed


def filter_sarif_by_confirmed(sarif: dict, confirmed_endpoints: set[tuple], rule_cwe: dict[str, int]) -> dict:
    """Filter SARIF to only include confirmed vulnerabilities"""
    filtered_sarif = copy.deepcopy(sarif)
    filtered_results = []
    for result in sarif["runs"][0]["results"]:
        rule_id = result["ruleId"]
        cwe_num = rule_cwe.get(rule_id)
        if not cwe_num:
            continue
        for related in result.get("relatedLocations", []):
            for loc in related.get("logicalLocations", []):
                fqn = loc.get("fullyQualifiedName", "")
                if " " not in fqn:
                    continue
                method, path = fqn.split(" ", 1)
                if (method, path, cwe_num) in confirmed_endpoints:
                    filtered_results.append(result)
                    break
            else:
                continue
            break

    filtered_sarif["runs"][0]["results"] = filtered_results
    logger.info(f"Filtered SARIF: {len(sarif['runs'][0]['results'])} -> {len(filtered_results)} results")
    return filtered_sarif


def main():
    parser = argparse.ArgumentParser(description="Filter SARIF based on confirmed ZAP vulnerabilities")

    parser.add_argument("--sarif", type=Path, required=True, help="SARIF file to filter")
    parser.add_argument("--report", type=Path, required=True, help="ZAP JSON report with confirmed vulnerabilities")
    parser.add_argument(
        "--output", type=Path, default=Path("scan-results/validated.sarif"), help="Output filtered SARIF file"
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="Enable verbose logging")

    args = parser.parse_args()
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    if not args.sarif.exists():
        logger.error(f"SARIF file not found: {args.sarif}")
        return 1
    if not args.report.exists():
        logger.error(f"Report file not found: {args.report}")
        return 1

    try:
        logger.debug(f"Loading SARIF: {args.sarif}")
        with open(args.sarif) as f:
            sarif = json.load(f)
        logger.debug(f"Loading ZAP report: {args.report}")
        with open(args.report) as f:
            zap_report = json.load(f)

        rule_cwe = extract_rule_cwe_mapping(sarif)
        logger.debug(f"Extracted {len(rule_cwe)} rule-to-CWE mappings")
        confirmed_endpoints = extract_confirmed_endpoints(zap_report)
        filtered_sarif = filter_sarif_by_confirmed(sarif, confirmed_endpoints, rule_cwe)
        filtered_sarif["runs"][0]["tool"]["driver"]["name"] = TOOL_NAME
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with open(args.output, "w") as f:
            json.dump(filtered_sarif, f, indent=2)
        logger.info(f"Saved filtered SARIF to: {args.output}")
        return 0
    except Exception as e:
        logger.error(f"Failed to filter SARIF: {e}", exc_info=args.verbose)
        return 1


if __name__ == "__main__":
    exit(main())
