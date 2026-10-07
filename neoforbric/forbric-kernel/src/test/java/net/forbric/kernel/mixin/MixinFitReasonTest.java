package net.forbric.kernel.mixin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class MixinFitReasonTest {
    @Test void repeatedAnchorTextIsRenderedOnceWithoutChangingCountsOrEvidence() {
        List<String> evidence = List.of("same anchor; same caller", "same anchor; same caller", "another anchor");
        var result = new MixinFit.Result(MixinFit.Verdict.PARTIAL, evidence, 27, 45, List.of());
        assertEquals("27/45 anchors resolve, missing: same anchor; same caller, another anchor", result.reason());
        assertEquals(evidence, result.unresolved()); assertEquals(27, result.resolved()); assertEquals(45, result.total());
    }
}
