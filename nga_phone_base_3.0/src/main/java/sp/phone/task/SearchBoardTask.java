package sp.phone.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;

import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel;
import gov.anzong.androidnga.core.board.data.BoardEntity;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import sp.phone.http.OnSimpleHttpCallBack;
import sp.phone.rxjava.BaseSubscriber;
import sp.phone.util.StringUtils;

/**
 * Created by Justwen on 2018/10/12.
 */
public class SearchBoardTask {


    public static void execute(String boardName, OnSimpleHttpCallBack<BoardEntity> callBack) {
        RetrofitHelper.getInstance()
                .getService()
                .get("http://bbs.nga.cn/forum.php?&__output=8&key=" + StringUtils.encodeUrl(boardName, "gbk"))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map(s -> {
                    try {
                        SearchResult decoded = decodeBoard(s);
                        int fid = decoded.fid;
                        String title = decoded.title;
                        BoardEntity board = ForumBoardViewModel.INSTANCE.findBoard(fid, 0);
                        if (board == null) {
                            board = new BoardEntity();
                            board.setFid(fid);
                            board.setName(title);
                        }
                        return board;

                    } catch (Exception e) {

                    }
                    return null;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new BaseSubscriber<BoardEntity>() {
                    @Override
                    public void onNext(BoardEntity board) {
                        callBack.onResult(board);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        callBack.onResult(null);
                    }
                });
    }
    static SearchResult decodeBoard(String payload) {
        JSONObject obj = JSON.parseObject(payload).getJSONObject("data").getJSONObject("0");
        return new SearchResult(obj.getInteger("fid"), obj.getString("name"));
    }

    static final class SearchResult {
        final int fid;
        final String title;
        private SearchResult(int fid, String title) { this.fid = fid; this.title = title; }
    }
}
