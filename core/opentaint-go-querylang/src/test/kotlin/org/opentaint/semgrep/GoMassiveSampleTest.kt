package org.opentaint.semgrep

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.writeText

class GoMassiveSampleTest : GoSampleBasedTestBase("GO_MASSIVE_SAMPLES_DIR") {
    private val outputDir: Path by lazy {
        val prop = System.getProperty("GO_MASSIVE_OUTPUT_DIR")
            ?: error("System property GO_MASSIVE_OUTPUT_DIR not set; check build.gradle.kts wiring")
        Path(prop).also { it.toFile().mkdirs() }
    }

    override val tracker = ExternalMethodTracker()

    override fun onTearDown() {
        super.onTearDown()

        val ext = tracker.getExternalMethods()
        val withoutRules = ext.withoutRules.joinToString("\n") {
            "${it.callSites.toString().padStart(4)}  ${it.method}  [${it.factPositions.joinToString(",")}]"
        }
        val withRules = ext.withRules.joinToString("\n") {
            "${it.callSites.toString().padStart(4)}  ${it.method}  [${it.factPositions.joinToString(",")}]"
        }
        outputDir.resolve("external-methods-without-rules.txt").writeText(withoutRules + "\n")
        outputDir.resolve("external-methods-with-rules.txt").writeText(withRules + "\n")
    }

    @Test
    fun cmdinj01EnvBasic() = runSampleDefault("cmdinj_01_env_basic")

    @Test
    fun cmdinj02ArgsDirect() = runSampleDefault("cmdinj_02_args_direct")

    @Test
    fun cmdinj03FormvalueLower() = runSampleDefault("cmdinj_03_formvalue_lower")

    @Test
    fun cmdinj04QueryUpper() = runSampleDefault("cmdinj_04_query_upper")

    @Test
    fun cmdinj05HeaderTrim() = runSampleDefault("cmdinj_05_header_trim")

    @Test
    fun cmdinj06EnvHelper1() = runSampleDefault("cmdinj_06_env_helper1")

    @Test
    fun cmdinj07ArgsHelper2() = runSampleDefault("cmdinj_07_args_helper2")

    @Test
    fun cmdinj08EnvIfBranch() = runSampleDefault("cmdinj_08_env_if_branch")

    @Test
    fun cmdinj09ArgsSwitch() = runSampleDefault("cmdinj_09_args_switch")

    @Test
    fun cmdinj10FormvalueFor() = runSampleDefault("cmdinj_10_formvalue_for")

    @Test
    fun cmdinj11EnvDefer() = runSampleDefault("cmdinj_11_env_defer")

    @Test
    fun cmdinj12QueryClosure() = runSampleDefault("cmdinj_12_query_closure")

    @Test
    fun cmdinj13EnvReplace() = runSampleDefault("cmdinj_13_env_replace")

    @Test
    fun cmdinj14ArgsReplaceall() = runSampleDefault("cmdinj_14_args_replaceall")

    @Test
    fun cmdinj15HeaderSanitizer() = runSampleDefault("cmdinj_15_header_sanitizer")

    @Test
    fun cmdinj16EnvBranchSanitizer() = runSampleDefault("cmdinj_16_env_branch_sanitizer")

    @Test
    fun cmdinj17FormvalueRegexSanitizer() = runSampleDefault("cmdinj_17_formvalue_regex_sanitizer")

    @Test
    fun cmdinj18ArgsAtoiLost() = runSampleDefault("cmdinj_18_args_atoi_lost")

    @Test
    fun cmdinj19QueryTrim() = runSampleDefault("cmdinj_19_query_trim")

    @Test
    fun cmdinj20EnvFullChain() = runSampleDefault("cmdinj_20_env_full_chain")

    @Test
    fun creds01EnvErrorf() = runSampleDefault("creds_01_env_errorf")

    @Test
    fun creds02EnvLogprintf() = runSampleDefault("creds_02_env_logprintf")

    @Test
    fun creds03EnvResponsewrite() = runSampleDefault("creds_03_env_responsewrite")

    @Test
    fun creds04EnvResponseheader() = runSampleDefault("creds_04_env_responseheader")

    @Test
    fun creds05ArgsWritefile() = runSampleDefault("creds_05_args_writefile")

    @Test
    fun creds06EnvSubprocessenv() = runSampleDefault("creds_06_env_subprocessenv")

    @Test
    fun creds07FormPasswordLog() = runSampleDefault("creds_07_form_password_log")

    @Test
    fun creds08HeaderAuthLog() = runSampleDefault("creds_08_header_auth_log")

    @Test
    fun creds09EnvTolowerLog() = runSampleDefault("creds_09_env_tolower_log")

    @Test
    fun creds10EnvHelperLog() = runSampleDefault("creds_10_env_helper_log")

    @Test
    fun creds11EnvBranchLog() = runSampleDefault("creds_11_env_branch_log")

    @Test
    fun creds12EnvDeferLog() = runSampleDefault("creds_12_env_defer_log")

    @Test
    fun creds13EnvClosureLog() = runSampleDefault("creds_13_env_closure_log")

    @Test
    fun creds14EnvLogoutputMultiarg() = runSampleDefault("creds_14_env_logoutput_multiarg")

    @Test
    fun creds15EnvTelemetry() = runSampleDefault("creds_15_env_telemetry")

    @Test
    fun creds16EnvMergeLog() = runSampleDefault("creds_16_env_merge_log")

    @Test
    fun creds17EnvRedactSanitizer() = runSampleDefault("creds_17_env_redact_sanitizer")

    @Test
    fun creds18QueryPostformExfil() = runSampleDefault("creds_18_query_postform_exfil")

    @Test
    fun creds19EnvIowritestring() = runSampleDefault("creds_19_env_iowritestring")

    @Test
    fun creds20EnvVariadicSprintfLog() = runSampleDefault("creds_20_env_variadic_sprintf_log")

    @Test
    fun crypto01EnvMd5Sum() = runSampleDefault("crypto_01_env_md5_sum")

    @Test
    fun crypto02QuerySha1Write() = runSampleDefault("crypto_02_query_sha1_write")

    @Test
    fun crypto03FormDesKey() = runSampleDefault("crypto_03_form_des_key")

    @Test
    fun crypto04HeaderMd5Write() = runSampleDefault("crypto_04_header_md5_write")

    @Test
    fun crypto05ArgsSha1Sum() = runSampleDefault("crypto_05_args_sha1_sum")

    @Test
    fun crypto06EnvHexMd5() = runSampleDefault("crypto_06_env_hex_md5")

    @Test
    fun crypto07FormBcryptStub() = runSampleDefault("crypto_07_form_bcrypt_stub")

    @Test
    fun crypto08EnvAesKey() = runSampleDefault("crypto_08_env_aes_key")

    @Test
    fun crypto09EnvHmacKey() = runSampleDefault("crypto_09_env_hmac_key")

    @Test
    fun crypto10EnvLowerMd5() = runSampleDefault("crypto_10_env_lower_md5")

    @Test
    fun crypto11QueryBranchSha1() = runSampleDefault("crypto_11_query_branch_sha1")

    @Test
    fun crypto12EnvDeferMd5() = runSampleDefault("crypto_12_env_defer_md5")

    @Test
    fun crypto13QueryClosureMd5() = runSampleDefault("crypto_13_query_closure_md5")

    @Test
    fun crypto14FormCbcIv() = runSampleDefault("crypto_14_form_cbc_iv")

    @Test
    fun crypto15EnvMd5Stub() = runSampleDefault("crypto_15_env_md5_stub")

    @Test
    fun crypto16MergeSha256() = runSampleDefault("crypto_16_merge_sha256")

    @Test
    fun crypto17NormalizeSanitizer() = runSampleDefault("crypto_17_normalize_sanitizer")

    @Test
    fun crypto18EnvRandSeed() = runSampleDefault("crypto_18_env_rand_seed")

    @Test
    fun crypto19QueryCtcmp() = runSampleDefault("crypto_19_query_ctcmp")

    @Test
    fun crypto20FormSprintfMd5() = runSampleDefault("crypto_20_form_sprintf_md5")

    @Test
    fun deser01EnvXmlBasic() = runSampleDefault("deser_01_env_xml_basic")

    @Test
    fun deser02QueryJson() = runSampleDefault("deser_02_query_json")

    @Test
    fun deser03FormSprintfXml() = runSampleDefault("deser_03_form_sprintf_xml")

    @Test
    fun deser04HeaderJson() = runSampleDefault("deser_04_header_json")

    @Test
    fun deser05ArgsXml() = runSampleDefault("deser_05_args_xml")

    @Test
    fun deser06CookieJson() = runSampleDefault("deser_06_cookie_json")

    @Test
    fun deser07BodyJson() = runSampleDefault("deser_07_body_json")

    @Test
    fun deser08GobNested() = runSampleDefault("deser_08_gob_nested")

    @Test
    fun deser09EnvHelperXml() = runSampleDefault("deser_09_env_helper_xml")

    @Test
    fun deser10BranchJson() = runSampleDefault("deser_10_branch_json")

    @Test
    fun deser11LoopXml() = runSampleDefault("deser_11_loop_xml")

    @Test
    fun deser12DeferJson() = runSampleDefault("deser_12_defer_json")

    @Test
    fun deser13ClosureJson() = runSampleDefault("deser_13_closure_json")

    @Test
    fun deser14XmlMultiarg() = runSampleDefault("deser_14_xml_multiarg")

    @Test
    fun deser15XmlDecoderChain() = runSampleDefault("deser_15_xml_decoder_chain")

    @Test
    fun deser16MergeSourcesJson() = runSampleDefault("deser_16_merge_sources_json")

    @Test
    fun deser17YamlStub() = runSampleDefault("deser_17_yaml_stub")

    @Test
    fun deser18SanitizerSchema() = runSampleDefault("deser_18_sanitizer_schema")

    @Test
    fun deser19EnvJsonDecoder() = runSampleDefault("deser_19_env_json_decoder")

    @Test
    fun deser20VariadicSprintfXml() = runSampleDefault("deser_20_variadic_sprintf_xml")

    @Test
    fun hdrinj01EnvRedirect() = runSampleDefault("hdrinj_01_env_redirect")

    @Test
    fun hdrinj02EnvRedirectConcat() = runSampleDefault("hdrinj_02_env_redirect_concat")

    @Test
    fun hdrinj03FormRedirect() = runSampleDefault("hdrinj_03_form_redirect")

    @Test
    fun hdrinj04FormRedirectSprintf() = runSampleDefault("hdrinj_04_form_redirect_sprintf")

    @Test
    fun hdrinj05QueryRedirect() = runSampleDefault("hdrinj_05_query_redirect")

    @Test
    fun hdrinj06QueryRedirectStatus() = runSampleDefault("hdrinj_06_query_redirect_status")

    @Test
    fun hdrinj07HeaderRedirectHelper() = runSampleDefault("hdrinj_07_header_redirect_helper")

    @Test
    fun hdrinj08FormRedirectRegexSanitizer() =
        runSampleDefault("hdrinj_08_form_redirect_regex_sanitizer")

    @Test
    fun ldap01EnvBasic() = runSampleDefault("ldap_01_env_basic")

    @Test
    fun ldap02EnvConcat() = runSampleDefault("ldap_02_env_concat")

    @Test
    fun ldap03FormSprintf() = runSampleDefault("ldap_03_form_sprintf")

    @Test
    fun ldap04FormConcat() = runSampleDefault("ldap_04_form_concat")

    @Test
    fun ldap05QueryTolower() = runSampleDefault("ldap_05_query_tolower")

    @Test
    fun ldap06QueryTrim() = runSampleDefault("ldap_06_query_trim")

    @Test
    fun ldap07FormWrapfilter() = runSampleDefault("ldap_07_form_wrapfilter")

    @Test
    fun ldap08FormEscapeSanitizer() = runSampleDefault("ldap_08_form_escape_sanitizer")

    @Test
    fun loginj01EnvLogprintf() = runSampleDefault("loginj_01_env_logprintf")

    @Test
    fun loginj02EnvLogprintfConcat() = runSampleDefault("loginj_02_env_logprintf_concat")

    @Test
    fun loginj03FormLogprint() = runSampleDefault("loginj_03_form_logprint")

    @Test
    fun loginj04PostformLogprint() = runSampleDefault("loginj_04_postform_logprint")

    @Test
    fun loginj05QueryLogoutput() = runSampleDefault("loginj_05_query_logoutput")

    @Test
    fun loginj06QueryLogoutputSprintf() = runSampleDefault("loginj_06_query_logoutput_sprintf")

    @Test
    fun loginj07EnvTolowerLogprintf() = runSampleDefault("loginj_07_env_tolower_logprintf")

    @Test
    fun loginj08EnvEscapeSanitizer() = runSampleDefault("loginj_08_env_escape_sanitizer")

    @Test
    fun nosql01EnvBasic() = runSampleDefault("nosql_01_env_basic")

    @Test
    fun nosql02EnvConcat() = runSampleDefault("nosql_02_env_concat")

    @Test
    fun nosql03FormSprintf() = runSampleDefault("nosql_03_form_sprintf")

    @Test
    fun nosql04FormConcat() = runSampleDefault("nosql_04_form_concat")

    @Test
    fun nosql05QueryBasic() = runSampleDefault("nosql_05_query_basic")

    @Test
    fun nosql06QueryTrim() = runSampleDefault("nosql_06_query_trim")

    @Test
    fun nosql07FormWrapmongo() = runSampleDefault("nosql_07_form_wrapmongo")

    @Test
    fun nosql08FormSchemaSanitizer() = runSampleDefault("nosql_08_form_schema_sanitizer")

    @Test
    fun path01EnvOpen() = runSampleDefault("path_01_env_open")

    @Test
    fun path02QueryJoinOpenfile() = runSampleDefault("path_02_query_join_openfile")

    @Test
    fun path03FormReadfile() = runSampleDefault("path_03_form_readfile")

    @Test
    fun path04HeaderSprintfOpen() = runSampleDefault("path_04_header_sprintf_open")

    @Test
    fun path05ArgsIoutilReadfile() = runSampleDefault("path_05_args_ioutil_readfile")

    @Test
    fun path06UrlpathTrimprefixReadfile() = runSampleDefault("path_06_urlpath_trimprefix_readfile")

    @Test
    fun path07MultiargOpenfile() = runSampleDefault("path_07_multiarg_openfile")

    @Test
    fun path08CookieOpen() = runSampleDefault("path_08_cookie_open")

    @Test
    fun path09JsonStructOpen() = runSampleDefault("path_09_json_struct_open")

    @Test
    fun path10HelperChainOpen() = runSampleDefault("path_10_helper_chain_open")

    @Test
    fun path11BranchRemove() = runSampleDefault("path_11_branch_remove")

    @Test
    fun path12LoopRemove() = runSampleDefault("path_12_loop_remove")

    @Test
    fun path13DeferRemove() = runSampleDefault("path_13_defer_remove")

    @Test
    fun path14ClosureOpen() = runSampleDefault("path_14_closure_open")

    @Test
    fun path15TwoSourcesOpen() = runSampleDefault("path_15_two_sources_open")

    @Test
    fun path16HelperSinkFileread() = runSampleDefault("path_16_helper_sink_fileread")

    @Test
    fun path17AllowlistSanitizer() = runSampleDefault("path_17_allowlist_sanitizer")

    @Test
    fun path18EnvFilepathWalk() = runSampleDefault("path_18_env_filepath_walk")

    @Test
    fun path19LookupenvOpen() = runSampleDefault("path_19_lookupenv_open")

    @Test
    fun path20VariadicSprintfOpen() = runSampleDefault("path_20_variadic_sprintf_open")

    @Test
    fun redir01EnvBasic() = runSampleDefault("redir_01_env_basic")

    @Test
    fun redir02EnvSprintf() = runSampleDefault("redir_02_env_sprintf")

    @Test
    fun redir03QueryBasic() = runSampleDefault("redir_03_query_basic")

    @Test
    fun redir04QueryConcat() = runSampleDefault("redir_04_query_concat")

    @Test
    fun redir05FormBasic() = runSampleDefault("redir_05_form_basic")

    @Test
    fun redir06PostformTrim() = runSampleDefault("redir_06_postform_trim")

    @Test
    fun redir07FormBuildurlHelper() = runSampleDefault("redir_07_form_buildurl_helper")

    @Test
    fun redir08FormAllowlistSanitizer() = runSampleDefault("redir_08_form_allowlist_sanitizer")

    @Test
    fun sqlinj01EnvBasic() = runSampleDefault("sqlinj_01_env_basic")

    @Test
    fun sqlinj02ArgsDirect() = runSampleDefault("sqlinj_02_args_direct")

    @Test
    fun sqlinj03FormvalueLower() = runSampleDefault("sqlinj_03_formvalue_lower")

    @Test
    fun sqlinj04QueryUpper() = runSampleDefault("sqlinj_04_query_upper")

    @Test
    fun sqlinj05HeaderTrim() = runSampleDefault("sqlinj_05_header_trim")

    @Test
    fun sqlinj06EnvHelper1() = runSampleDefault("sqlinj_06_env_helper1")

    @Test
    fun sqlinj07ArgsHelper2() = runSampleDefault("sqlinj_07_args_helper2")

    @Test
    fun sqlinj08EnvIfBranch() = runSampleDefault("sqlinj_08_env_if_branch")

    @Test
    fun sqlinj09ArgsSwitch() = runSampleDefault("sqlinj_09_args_switch")

    @Test
    fun sqlinj10FormvalueFor() = runSampleDefault("sqlinj_10_formvalue_for")

    @Test
    fun sqlinj11EnvDefer() = runSampleDefault("sqlinj_11_env_defer")

    @Test
    fun sqlinj12QueryClosure() = runSampleDefault("sqlinj_12_query_closure")

    @Test
    fun sqlinj13EnvReplace() = runSampleDefault("sqlinj_13_env_replace")

    @Test
    fun sqlinj14ArgsReplaceall() = runSampleDefault("sqlinj_14_args_replaceall")

    @Test
    fun sqlinj15HeaderSanitizer() = runSampleDefault("sqlinj_15_header_sanitizer")

    @Test
    fun sqlinj16EnvBranchSanitizer() = runSampleDefault("sqlinj_16_env_branch_sanitizer")

    @Test
    fun sqlinj17FormvalueRegexSanitizer() = runSampleDefault("sqlinj_17_formvalue_regex_sanitizer")

    @Test
    fun sqlinj18ArgsAtoiLost() = runSampleDefault("sqlinj_18_args_atoi_lost")

    @Test
    fun sqlinj19QueryTrim() = runSampleDefault("sqlinj_19_query_trim")

    @Test
    fun sqlinj20EnvFullChain() = runSampleDefault("sqlinj_20_env_full_chain")

    @Test
    fun ssrf01EnvGet() = runSampleDefault("ssrf_01_env_get")

    @Test
    fun ssrf02QueryGet() = runSampleDefault("ssrf_02_query_get")

    @Test
    fun ssrf03FormSprintf() = runSampleDefault("ssrf_03_form_sprintf")

    @Test
    fun ssrf04HeaderNewrequest() = runSampleDefault("ssrf_04_header_newrequest")

    @Test
    fun ssrf05QueryConcatScheme() = runSampleDefault("ssrf_05_query_concat_scheme")

    @Test
    fun ssrf06QueryPath() = runSampleDefault("ssrf_06_query_path")

    @Test
    fun ssrf07MultiargNewrequestUrl() = runSampleDefault("ssrf_07_multiarg_newrequest_url")

    @Test
    fun ssrf08StdinPost() = runSampleDefault("ssrf_08_stdin_post")

    @Test
    fun ssrf09CookieGet() = runSampleDefault("ssrf_09_cookie_get")

    @Test
    fun ssrf10FormHelperChain() = runSampleDefault("ssrf_10_form_helper_chain")

    @Test
    fun ssrf11BranchPostform() = runSampleDefault("ssrf_11_branch_postform")

    @Test
    fun ssrf12LoopGet() = runSampleDefault("ssrf_12_loop_get")

    @Test
    fun ssrf13DeferGet() = runSampleDefault("ssrf_13_defer_get")

    @Test
    fun ssrf14ClosureGet() = runSampleDefault("ssrf_14_closure_get")

    @Test
    fun ssrf15TwoSourcesMerge() = runSampleDefault("ssrf_15_two_sources_merge")

    @Test
    fun ssrf16HelperSinkHttpfetch() = runSampleDefault("ssrf_16_helper_sink_httpfetch")

    @Test
    fun ssrf17AllowlistSanitizer() = runSampleDefault("ssrf_17_allowlist_sanitizer")

    @Test
    fun ssrf18EnvNewrequestctx() = runSampleDefault("ssrf_18_env_newrequestctx")

    @Test
    fun ssrf19ParseRecompose() = runSampleDefault("ssrf_19_parse_recompose")

    @Test
    fun ssrf20VariadicSprintf() = runSampleDefault("ssrf_20_variadic_sprintf")

    @Test
    fun xss01EnvWrite() = runSampleDefault("xss_01_env_write")

    @Test
    fun xss03FormSprintfWrite() = runSampleDefault("xss_03_form_sprintf_write")

    @Test
    fun xss04HeaderIowrite() = runSampleDefault("xss_04_header_iowrite")

    @Test
    fun xss05UrlpathWrite() = runSampleDefault("xss_05_urlpath_write")

    @Test
    fun xss06CookieWrite() = runSampleDefault("xss_06_cookie_write")

    @Test
    fun xss07JsonFieldWrite() = runSampleDefault("xss_07_json_field_write")

    @Test
    fun xss08HelperBadEscape() = runSampleDefault("xss_08_helper_bad_escape")

    @Test
    fun xss09IfBranch() = runSampleDefault("xss_09_if_branch")

    @Test
    fun xss10LoopRange() = runSampleDefault("xss_10_loop_range")

    @Test
    fun xss11DeferWrite() = runSampleDefault("xss_11_defer_write")

    @Test
    fun xss12ClosureWrite() = runSampleDefault("xss_12_closure_write")

    @Test
    fun xss13TemplateExecuteData() = runSampleDefault("xss_13_template_execute_data")

    @Test
    fun xss14NestedEscapeMiss() = runSampleDefault("xss_14_nested_escape_miss")

    @Test
    fun xss15TwoSourcesMerge() = runSampleDefault("xss_15_two_sources_merge")

    @Test
    fun xss16ThirdPartyRenderer() = runSampleDefault("xss_16_third_party_renderer")

    @Test
    fun xss17HtmlEscapeSanitizer() = runSampleDefault("xss_17_html_escape_sanitizer")

    @Test
    fun xss18TemplateStructData() = runSampleDefault("xss_18_template_struct_data")

    @Test
    fun xss19WriteheaderThenWrite() = runSampleDefault("xss_19_writeheader_then_write")

    @Test
    fun xss20VariadicSprintf() = runSampleDefault("xss_20_variadic_sprintf")

    // ─── Star-operator ($*VAR) field-taint e2e samples (parity with Java StarSource/StarSink/StarSanitizer) ───

    @Test
    fun star01SinkField() = runSampleDefault("star_01_sink_field")

    @Test
    fun star02SourceField() = runSampleDefault("star_02_source_field")

    @Test
    fun star03SanitizerField() = runSampleDefault("star_03_sanitizer_field")

    // ─── Deep-nesting matrix: taint hidden 5+ fields deep and/or 5+ calls deep ───

    @Test
    fun star04DeepSinkField() = runSampleDefault("star_04_deep_sink_field")

    @Test
    fun star05DeepSourceField() = runSampleDefault("star_05_deep_source_field")

    @Test
    fun star06DeepSanitizerField() = runSampleDefault("star_06_deep_sanitizer_field")

    @Test
    fun star07InterprocChain() = runSampleDefault("star_07_interproc_chain")

    @Test
    fun star08SourceAndSink() = runSampleDefault("star_08_source_and_sink")

    // ─── Star matrix: 5x interproc depth combined with 5x field depth, one sample per star feature
    //     (parity with Java StarMatrix*) ───

    @Test
    fun star09MatrixSource() = runSampleDefault("star_09_matrix_source")

    @Test
    fun star10MatrixSink() = runSampleDefault("star_10_matrix_sink")

    @Test
    fun star11MatrixPropagator() = runSampleDefault("star_11_matrix_propagator")

    @Test
    fun star12MatrixSanitizer() = runSampleDefault("star_12_matrix_sanitizer")

    @Test
    fun star13MatrixPatternNot() = runSampleDefault("star_13_matrix_pattern_not")

    @Test
    fun star14MatrixPatternInside() = runSampleDefault("star_14_matrix_pattern_inside")

    @Test
    fun star15MatrixPatternNotInside() = runSampleDefault("star_15_matrix_pattern_not_inside")

    private fun runSampleDefault(name: String) = runSample(name, useDefaultConfig = true)
}
