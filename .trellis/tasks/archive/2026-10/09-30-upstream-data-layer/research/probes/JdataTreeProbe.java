import java.nio.file.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import sp.phone.http.bean.TopicListBean;
public class JdataTreeProbe {
 static boolean good(TopicListBean b){return b!=null && b.getData()!=null && b.getData().get__F()!=null && "synthetic".equals(b.getData().get__F().name);}
 public static void main(String[] args)throws Exception {
  try(var files=Files.list(Path.of(args[0]))) { for(Path p: files.sorted().toList()) {
   String s=Files.readString(p);
   try {System.out.println(p.getFileName()+" typed="+good(JSON.parseObject(s,TopicListBean.class)));} catch(Exception e){System.out.println(p.getFileName()+" typed="+e.getClass().getSimpleName());}
   try {System.out.println(p.getFileName()+" tree="+good(JSON.parseObject(s,JSONReader.Feature.DisableReferenceDetect).toJavaObject(TopicListBean.class, JSONReader.Feature.SupportSmartMatch)));} catch(Exception e){System.out.println(p.getFileName()+" tree="+e.getClass().getSimpleName());}
  }}
 }
}
