package cn.lvxu.travel;
public final class ApiHttpErrorTest {
 public static void main(String[] args){int n=0;for(int code:new int[]{400,401,402,403,404,408,413,429,500,502,504,301}){String message=ApiHttp.httpError(code);if(!message.startsWith("HTTP "+code+"：")||!message.matches(".*[\u4e00-\u9fff].*"))throw new AssertionError("missing actionable HTTP error");n++;}if(!ApiHttp.httpError(401).contains("国内")||!ApiHttp.httpError(403).contains("开通"))throw new AssertionError("auth failure distinctions");System.out.println("PASS: "+(n+1)+" HTTP error assertions");}
}
