package link.thingscloud.opensips.event.util;

import java.util.Random;

/**
 * <p>RandomUtils class.</p>
 *
 * @author zhouhailin
 * @version 1.0.0
 */
public class RandomUtils {

    private static final Random RANDOM = new Random();

    /**
     * <p>nextInt.</p>
     *
     * @param startInclusive a int.
     * @param endExclusive   a int.
     * @return a int.
     */
    public static int nextInt(final int startInclusive, final int endExclusive) {
        Validate.isTrue(endExclusive >= startInclusive,
                "Start value must be smaller or equal to end value.");
        Validate.isTrue(startInclusive >= 0, "Both range values must be non-negative.");

        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + RANDOM.nextInt(endExclusive - startInclusive);
    }
}
