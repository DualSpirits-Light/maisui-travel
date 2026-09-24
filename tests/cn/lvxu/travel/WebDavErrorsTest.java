package cn.lvxu.travel;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLHandshakeException;

/** Behavioural contract for WebDAV failures shown to the settings screen. */
public final class WebDavErrorsTest {
 public static void main(String[] args) {
  int assertions = 0;
  int[] codes = {401, 403, 404, 405, 429, 500, 502, 503, 599};
  for (int code : codes) {
   String message = WebDavService.errorForStatus(code);
   if (!message.startsWith("WebDAV 返回 " + code + "：") || !message.matches(".*[\u4e00-\u9fff].*")) {
    throw new AssertionError("status " + code + " must be a Chinese, actionable message: " + message);
   }
   assertions++;
  }
  if (!WebDavService.errorForStatus(401).contains("未授权")) throw new AssertionError("401 classification");
  if (!WebDavService.errorForStatus(403).contains("权限不足")) throw new AssertionError("403 classification");
  if (!WebDavService.errorForStatus(404).contains("路径不存在")) throw new AssertionError("404 classification");
  if (!WebDavService.errorForStatus(405).contains("不支持")) throw new AssertionError("405 classification");
  if (!WebDavService.errorForStatus(429).contains("频繁")) throw new AssertionError("429 classification");
  if (!WebDavService.errorForStatus(503).contains("暂不可用")) throw new AssertionError("5xx classification");
  assertions += 6;
  if (!WebDavService.errorForTransport(new SocketTimeoutException("https://secret.example"), false).contains("超时")) throw new AssertionError("timeout classification");
  if (!WebDavService.errorForTransport(new UnknownHostException("secret.example"), false).contains("域名")) throw new AssertionError("DNS classification");
  if (!WebDavService.errorForTransport(new ConnectException("secret.example"), false).contains("连接")) throw new AssertionError("connect classification");
  if (!WebDavService.errorForTransport(new SSLHandshakeException("secret.example"), false).contains("证书")) throw new AssertionError("TLS classification");
  if (!WebDavService.errorForTransport(new java.io.InterruptedIOException("secret.example"), true).contains("取消")) throw new AssertionError("cancel classification");
  String safe = WebDavService.errorForTransport(new UnknownHostException("secret.example"), false);
  if (safe.contains("secret.example") || safe.matches(".*[A-Za-z]{5,}Exception.*")) throw new AssertionError("transport error leaks internals");
  assertions += 6;
  System.out.println("PASS: " + assertions + " WebDAV error assertions");
 }
}
