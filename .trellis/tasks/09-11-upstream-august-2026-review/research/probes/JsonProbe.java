import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import sp.phone.http.bean.TopicListBean;
public class JsonProbe {
  static String normalize(String js) { js = js.replaceAll("\"jdata\":\"(?:\\\\.|[^\"\\\\])*\",", ""); return js; }
  public static void main(String[] args) throws Exception {
    List<Path> fixtures;
    try (var stream=Files.list(Path.of(args[0]))) { fixtures=stream.sorted().toList(); }
    for (Path fixture: fixtures) {
      String input=Files.readString(fixture);
      for (String library: new String[]{"com.alibaba.fastjson.JSON","com.alibaba.fastjson2.JSON"}) {
        for (boolean sanitize: new boolean[]{false,true}) {
          String payload=sanitize?normalize(input):input;
          String outcome;
          try {
            TopicListBean bean=(TopicListBean)Class.forName(library).getMethod("parseObject",String.class,Class.class).invoke(null,payload,TopicListBean.class);
            outcome=(bean!=null && bean.getData()!=null && bean.getData().get__F()!=null && "synthetic".equals(bean.getData().get__F().name))?"PASS":"WRONG_DATA";
          } catch (InvocationTargetException e) { outcome="FAIL:"+e.getCause().getClass().getSimpleName(); }
          System.out.println(fixture.getFileName()+"\t"+library+"\t"+(sanitize?"upstream_regex":"raw")+"\t"+outcome+"\tchanged="+!input.equals(payload));
        }
      }
    }
  }
}
