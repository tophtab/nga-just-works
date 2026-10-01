import com.alibaba.fastjson2.JSONReader;
public class JsonBoundaryProbe {
 public static class LegacyBean { private int authorId; private String fromClient; public int getAuthorId(){return authorId;} public void setAuthorId(int x){authorId=x;} public String getFromClient(){return fromClient;} public void setFromClient(String x){fromClient=x;} }
 public static void main(String[] args) {
  String wire="{\"authorid\":42,\"from_client\":\"103 synthetic\"}";
  LegacyBean one=com.alibaba.fastjson.JSON.parseObject(wire, LegacyBean.class);
  LegacyBean two=com.alibaba.fastjson2.JSON.parseObject(wire, LegacyBean.class);
  LegacyBean smart=com.alibaba.fastjson2.JSON.parseObject(wire, LegacyBean.class, JSONReader.Feature.SupportSmartMatch);
  System.out.println("bean1="+one.getAuthorId()+"/"+one.getFromClient());
  System.out.println("bean2="+two.getAuthorId()+"/"+two.getFromClient());
  System.out.println("bean2-smart="+smart.getAuthorId()+"/"+smart.getFromClient());
  String[] cases={"{unquoted:3}","{\"@type\":\"not.a.LoadableClass\",\"$ref\":\"$\",\"number\":0.1}","{\"v\":1,\"nested\":{\"$ref\":\"$.v\"}}","{\"pageSize\":null,\"page\":1}","{\"ok\":1}{}","{\"jdata\":\"\\x5C\",\"name\":\"synthetic\"}","{\"n\":+123}","{\"n\":0123}"};
  for(String s:cases) {
   try { Object a=com.alibaba.fastjson.JSON.parse(s, new com.alibaba.fastjson.parser.ParserConfig(),com.alibaba.fastjson.parser.Feature.AutoCloseSource.mask|com.alibaba.fastjson.parser.Feature.DisableSpecialKeyDetect.mask|com.alibaba.fastjson.parser.Feature.UseBigDecimal.mask); System.out.println("old "+s+" => "+a); } catch(Exception e){System.out.println("old "+s+" => "+e.getClass().getSimpleName());}
   try { Object b=com.alibaba.fastjson2.JSON.parse(s,JSONReader.Feature.AllowUnQuotedFieldNames,JSONReader.Feature.DisableReferenceDetect,JSONReader.Feature.DisableSingleQuote);System.out.println("new "+s+" => "+b);if(b instanceof com.alibaba.fastjson2.JSONObject){Object n=((com.alibaba.fastjson2.JSONObject)b).get("number");if(n!=null)System.out.println("number-class="+n.getClass().getSimpleName());} }catch(Exception e){System.out.println("new "+s+" => "+e.getClass().getSimpleName());}
  }
 }
}
