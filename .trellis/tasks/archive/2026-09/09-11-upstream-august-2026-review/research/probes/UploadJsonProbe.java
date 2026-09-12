import com.alibaba.fastjson2.JSONReader;
public class UploadJsonProbe {
  public static void main(String[] args) {
    String[] cases={"{\"data\":{\"url\":\"https://example.invalid/a\"}}", "{data:{url:\"https://example.invalid/a\"}}"};
    for(int i=0;i<cases.length;i++) {
      String input=cases[i];
      for(int mode=0;mode<3;mode++) {
        String outcome;
        try {
          String url;
          if(mode==0) url=com.alibaba.fastjson.JSON.parseObject(input).getJSONObject("data").getString("url");
          else if(mode==1) url=com.alibaba.fastjson2.JSON.parseObject(input).getJSONObject("data").getString("url");
          else url=com.alibaba.fastjson2.JSON.parseObject(input, JSONReader.Feature.AllowUnQuotedFieldNames).getJSONObject("data").getString("url");
          outcome="https://example.invalid/a".equals(url)?"PASS":"WRONG_DATA";
        } catch(Exception e) {outcome="FAIL:"+e.getClass().getSimpleName();}
        System.out.println((i==0?"quoted":"unquoted")+"\t"+new String[]{"fastjson1_default","fastjson2_default","fastjson2_AllowUnQuotedFieldNames"}[mode]+"\t"+outcome);
      }
    }
  }
}
