package sp.phone.view;

import java.util.List;
import java.util.Random;

/** Keeps only the previous resource ID, so separate loading views share repeat avoidance. */
final class LoadingTipSelector {

    private final Random random;
    private int previousTip;

    LoadingTipSelector(Random random) {
        this.random = random;
    }

    /** The catalog contains unique, nonzero string resource IDs. Zero means no tip. */
    int next(List<Integer> eligibleTips) {
        if (eligibleTips.isEmpty()) {
            return 0;
        }

        int previousIndex = eligibleTips.indexOf(previousTip);
        boolean excludePrevious = eligibleTips.size() > 1 && previousIndex >= 0;
        int choiceCount = eligibleTips.size() - (excludePrevious ? 1 : 0);
        int index = random.nextInt(choiceCount);
        if (excludePrevious && index >= previousIndex) {
            index++;
        }

        previousTip = eligibleTips.get(index);
        return previousTip;
    }
}
