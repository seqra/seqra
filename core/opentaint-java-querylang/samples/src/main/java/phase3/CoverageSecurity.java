package phase3;

import base.RuleSample;
import base.RuleSet;

import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.cert.Certificate;

// Phase 3 stdlib coverage: java.security.CodeSource passthrough entries touched
// by the redundant-star cleanup. Each Positive flows taint from a source array,
// through the CodeSource constructor field store, back out through the matching
// accessor, to a sink. A Positive turning red means the config dropped a flow.
@RuleSet("phase3/CoverageSecurity.yaml")
public abstract class CoverageSecurity implements RuleSample {
    public Certificate[] certSrc() { return new Certificate[0]; }
    public CodeSigner[] signerSrc() { return new CodeSigner[0]; }

    public void objSink(Object o) {}

    // java.security.CodeSource#<init>(URL,Certificate[]) : arg1 -> this.certificates ;
    // getCertificates() : this.certificates -> result.
    static class PositiveCodeSourceCertificates extends CoverageSecurity {
        @Override public void entrypoint() {
            Certificate[] certs = certSrc();
            CodeSource cs = new CodeSource((java.net.URL) null, certs);
            Certificate[] got = cs.getCertificates();
            objSink(got);
        }
    }

    // java.security.CodeSource#<init>(URL,CodeSigner[]) : arg1 -> this.codeSigners ;
    // getCodeSigners() : this.codeSigners -> result.
    static class PositiveCodeSourceSigners extends CoverageSecurity {
        @Override public void entrypoint() {
            CodeSigner[] signers = signerSrc();
            CodeSource cs = new CodeSource((java.net.URL) null, signers);
            CodeSigner[] got = cs.getCodeSigners();
            objSink(got);
        }
    }

    // Negative: a clean local certificate array must not be reported.
    static class NegativeCleanCodeSource extends CoverageSecurity {
        @Override public void entrypoint() {
            Certificate[] certs = new Certificate[0];
            CodeSource cs = new CodeSource((java.net.URL) null, certs);
            Certificate[] got = cs.getCertificates();
            objSink(got);
        }
    }
}
