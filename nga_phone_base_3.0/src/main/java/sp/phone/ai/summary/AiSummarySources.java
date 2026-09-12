package sp.phone.ai.summary;

import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;

import gov.anzong.androidnga.common.util.ForumUtils;
import sp.phone.common.UserManagerImpl;

/** Legacy session interoperability stays here; UI and prompt objects never receive Cookies. */
public final class AiSummarySources {

    private AiSummarySources() {
    }

    public static SummaryController.InputSource floor(FloorSummaryInput input) {
        return (config, callback) -> {
            if (input.getBody().isEmpty()) {
                callback.onError("当前楼层没有可用于总结的文字");
            } else {
                callback.onSuccess(input.toPrompt());
            }
            return SummaryController.Cancelable.NONE;
        };
    }

    public static SummaryController.InputSource profile(String uid, String userName) {
        // Deferred until SummaryController has checked configuration. Each deliberate retry
        // captures one session for both first-page reads. There are no application retries,
        // pagination, or account rotation; idempotent transport follow-ups retain that snapshot.
        return (config, callback) -> {
            NgaProfilePageSource source = new NgaProfilePageSource(
                    ForumUtils.getAvailableDomain(),
                    UserManagerImpl.getInstance().getCookie(),
                    RetrofitHelper.getInstance().getUserAgent());
            return new ProfileSummaryLoader(source).load(uid, userName, config.getProfilePrompt(), callback);
        };
    }
}
