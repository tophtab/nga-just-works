package sp.phone.task;

import androidx.annotation.Nullable;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.Map;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ContextUtils;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import sp.phone.http.OnSimpleHttpCallBack;
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;
import com.justwen.androidnga.base.network.retrofit.RetrofitService;
import sp.phone.rxjava.BaseSubscriber;

/**
 * 赞或者踩
 * Created by elrond on 2017/9/1.
 */

public class LikeTask {

    public static final int SUPPORT = 1;

    public static final int OPPOSE = -1;

    /** The current user's vote on a post, as far as this session knows it. */
    public static final int STATE_NONE = 0;
    public static final int STATE_UP = 1;
    public static final int STATE_DOWN = -1;

    /** Result of a vote: the server message, the score change and the resulting vote state. */
    public interface VoteCallback {
        void onResult(String message, @Nullable Integer delta, int state);
    }

    private RetrofitService mService;

    private Map<String, String> mParamMap;

    public LikeTask() {
        mService = RetrofitHelper.getInstance().getService();
        mParamMap = new HashMap<>();
        mParamMap.put("__lib", "topic_recommend");
        mParamMap.put("__act", "add");
        mParamMap.put("raw", "3");
        mParamMap.put("pid", "0");
        mParamMap.put("__output", "8");
    }

    public void execute(int tid, int pid, int like, OnSimpleHttpCallBack<String> callBack) {
        vote(tid, pid, like, (message, delta, state) -> callBack.onResult(message));
    }

    /**
     * topic_recommend toggles: voting the same way again cancels, the other way switches. data["1"]
     * is the score change this request caused (+1, -1 for a cancel, -2 for up to down ...), which
     * also tells the resulting state; read.php does not report the user's own vote.
     */
    public void vote(int tid, int pid, int like, VoteCallback callBack) {
        Map<String, String> map = new HashMap<>(mParamMap);
        map.put("value", String.valueOf(like));
        map.put("tid", String.valueOf(tid));
        map.put("pid", String.valueOf(pid));
        mService.post(map)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new BaseSubscriber<String>() {
                    @Override
                    public void onNext(String s) {
                        String message;
                        try {
                            message = decodeResult(s);
                        } catch (Exception e) {
                            callBack.onResult(ContextUtils.getString(R.string.network_error), null, STATE_NONE);
                            return;
                        }
                        Integer delta = decodeDelta(s);
                        callBack.onResult(message, delta, delta == null ? STATE_NONE : stateAfter(like, delta));
                    }
                });

    }
    /** data["1"], the score change of this vote; null when absent or not a number. */
    @Nullable
    static Integer decodeDelta(String payload) {
        try {
            JSONObject data = JSON.parseObject(payload).getJSONObject("data");
            String value = data == null ? null : data.getString("1");
            return value == null ? null : Integer.valueOf(value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Expected state right after tapping [like] from [current]: the same vote cancels, the other switches. */
    public static int expectedState(int current, int like) {
        int target = like == SUPPORT ? STATE_UP : STATE_DOWN;
        return current == target ? STATE_NONE : target;
    }

    /** Vote state after voting [like] with the returned [delta] (MNGA uses the same rule). */
    static int stateAfter(int like, int delta) {
        if (like == SUPPORT && delta > 0) return STATE_UP;
        if (like == OPPOSE && delta < 0) return STATE_DOWN;
        return STATE_NONE;
    }

    static String decodeResult(String payload) {
        JSONObject data = JSON.parseObject(payload).getJSONObject("data");
        return data.getString("0");
    }

}
