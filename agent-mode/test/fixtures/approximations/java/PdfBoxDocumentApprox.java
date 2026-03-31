package agent.approximations;

import org.opentaint.ir.approximation.annotation.ApproximateByName;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;
import org.opentaint.jvm.dataflow.approximations.OpentaintNdUtil;

/**
 * Code-based approximation for PDFBox's PDDocument class.
 *
 * IMPORTANT: Approximations are ONLY applicable to external methods —
 * library classes whose source code is NOT part of the project being analyzed.
 * PDFBox is an external dependency of Stirling-PDF (pdfbox-3.0.6.jar).
 *
 * This models complex taint propagation through PDDocument methods that
 * involve internal state and cannot be expressed with simple YAML passThrough.
 *
 * PDDocument.save(OutputStream) — taint on the document (this) flows to
 * the output stream, modeling the case where a tainted PDF is serialized.
 */
@ApproximateByName("org.apache.pdfbox.pdmodel.PDDocument")
public class PdfBoxDocumentApprox {

    /**
     * Model save(OutputStream) — taint on this flows to arg(0).
     * A tainted document writes tainted bytes to the output stream.
     */
    public void save(java.io.OutputStream output) throws java.io.IOException {
        org.apache.pdfbox.pdmodel.PDDocument self =
            (org.apache.pdfbox.pdmodel.PDDocument) (Object) this;
        if (OpentaintNdUtil.nextBool()) {
            throw new java.io.IOException("approximation: failure path");
        }
        // Model: taint from document flows to output stream
        byte[] data = new byte[1];
        output.write(data);
    }

    /**
     * Model getPage(int) — taint on this flows to result.
     * A tainted document produces tainted pages.
     */
    public Object getPage(int pageIndex) {
        org.apache.pdfbox.pdmodel.PDDocument self =
            (org.apache.pdfbox.pdmodel.PDDocument) (Object) this;
        if (OpentaintNdUtil.nextBool()) {
            return null;
        }
        return self.getPages().get(pageIndex);
    }
}
