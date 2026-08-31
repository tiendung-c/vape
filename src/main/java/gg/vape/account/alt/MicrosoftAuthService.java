package gg.vape.account.alt;

import com.google.gson.*;
import gg.vape.Vape;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

/**
 * Microsoft OAuth flow ported from ksyzov/AccountManager without Apache HttpClient.
 * Uses HttpURLConnection + embedded HttpServer for callback.
 * Based on https://github.com/ksyzov/AccountManager and https://github.com/axieum/authme
 */
public final class MicrosoftAuthService {
    private static final String CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
    private static final int PORT = 25575;
    private static final Gson GSON = new Gson();

    private MicrosoftAuthService() {}

    public static URI getMSAuthLink(String state) {
        try {
            String redirect = String.format("http://localhost:%d/callback", PORT);
            String url = String.format(
                    "https://login.live.com/oauth20_authorize.srf?client_id=%s&response_type=code&redirect_uri=%s&scope=%s&state=%s&prompt=select_account",
                    URLEncoder.encode(CLIENT_ID, "UTF-8"),
                    URLEncoder.encode(redirect, "UTF-8"),
                    URLEncoder.encode("XboxLive.signin XboxLive.offline_access", "UTF-8"),
                    URLEncoder.encode(state, "UTF-8")
            );
            return new URI(url);
        } catch (Exception e) {
            return null;
        }
    }

    public static void openWebLink(URI uri) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (Exception ignored) {}
        try {
            // fallback via reflection for headless?
            Class<?> desktop = Class.forName("java.awt.Desktop");
            Object obj = desktop.getMethod("getDesktop").invoke(null);
            desktop.getMethod("browse", URI.class).invoke(obj, uri);
        } catch (Exception ignored) {}
    }

    public static CompletableFuture<String> acquireMSAuthCode(String state, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            HttpServer server = null;
            try {
                server = HttpServer.create(new InetSocketAddress(PORT), 0);
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> authCode = new AtomicReference<>(null);
                AtomicReference<String> errorMsg = new AtomicReference<>(null);

                server.createContext("/callback", exchange -> {
                    try {
                        String query = exchange.getRequestURI().getQuery();
                        if (query == null) query = "";
                        // also handle query in getRequestURI().toString if needed
                        Map<String, String> params = parseQuery(query);
                        // Fallback: parse full URI if query empty
                        if (params.isEmpty() && exchange.getRequestURI().toString().contains("?")) {
                            String full = exchange.getRequestURI().toString();
                            String qs = full.substring(full.indexOf('?') + 1);
                            params = parseQuery(qs);
                        }

                        if (!state.equals(params.get("state"))) {
                            errorMsg.set(String.format("State mismatch! Expected '%s' but got '%s'.", state, params.get("state")));
                        } else if (params.containsKey("code")) {
                            authCode.set(params.get("code"));
                        } else if (params.containsKey("error")) {
                            errorMsg.set(String.format("%s: %s", params.get("error"), params.getOrDefault("error_description", "")));
                        }

                        byte[] resp = buildCallbackHtml(authCode.get() != null);
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, resp.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(resp);
                        }
                    } catch (Exception e) {
                        Vape.logThrowable(e);
                    } finally {
                        latch.countDown();
                    }
                });

                server.start();
                latch.await(5, TimeUnit.MINUTES);

                String code = authCode.get();
                if (code != null && !code.trim().isEmpty()) return code;
                String err = errorMsg.get();
                throw new Exception(err != null ? err : "No auth code received (timeout or user cancelled)");

            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft auth cancelled");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft auth code", e);
            } finally {
                if (server != null) {
                    try { server.stop(2); } catch (Exception ignored) {}
                }
            }
        }, executor);
    }

    public static CompletableFuture<Map<String,String>> acquireMSAccessTokens(String authCode, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String redirect = String.format("http://localhost:%d/callback", PORT);
                Map<String,String> form = new LinkedHashMap<>();
                form.put("client_id", CLIENT_ID);
                form.put("grant_type", "authorization_code");
                form.put("code", authCode);
                form.put("redirect_uri", redirect);
                String resp = postForm("https://login.live.com/oauth20_token.srf", form);
                JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
                String access = optString(json, "access_token");
                String refresh = optString(json, "refresh_token");
                if (isBlank(access) || isBlank(refresh)) {
                    String err = json.has("error") ? json.get("error").getAsString() + ": " + optString(json, "error_description") : "Missing tokens";
                    throw new Exception(err);
                }
                Map<String,String> map = new HashMap<>();
                map.put("access_token", access);
                map.put("refresh_token", refresh);
                return map;
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft tokens", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String,String>> refreshMSAccessTokens(String refreshToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String redirect = String.format("http://localhost:%d/callback", PORT);
                Map<String,String> form = new LinkedHashMap<>();
                form.put("client_id", CLIENT_ID);
                form.put("grant_type", "refresh_token");
                form.put("refresh_token", refreshToken);
                form.put("redirect_uri", redirect);
                String resp = postForm("https://login.live.com/oauth20_token.srf", form);
                JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
                String access = optString(json, "access_token");
                String refresh = optString(json, "refresh_token");
                if (isBlank(access) || isBlank(refresh)) {
                    String err = json.has("error") ? json.get("error").getAsString() + ": " + optString(json, "error_description") : "Missing tokens";
                    throw new Exception(err);
                }
                Map<String,String> map = new HashMap<>();
                map.put("access_token", access);
                map.put("refresh_token", refresh);
                return map;
            } catch (Exception e) {
                throw new CompletionException("Unable to refresh Microsoft tokens", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireXboxAccessToken(String msAccessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject entity = new JsonObject();
                JsonObject props = new JsonObject();
                props.addProperty("AuthMethod", "RPS");
                props.addProperty("SiteName", "user.auth.xboxlive.com");
                props.addProperty("RpsTicket", "d=" + msAccessToken);
                entity.add("Properties", props);
                entity.addProperty("RelyingParty", "http://auth.xboxlive.com");
                entity.addProperty("TokenType", "JWT");
                String resp = postJson("https://user.auth.xboxlive.com/user/authenticate", entity.toString());
                JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
                String token = optString(json, "Token");
                if (isBlank(token)) {
                    String err = json.has("XErr") ? json.get("XErr").getAsString() + ": " + optString(json, "Message") : "Missing Xbox token";
                    throw new Exception(err);
                }
                return token;
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox token", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String,String>> acquireXboxXstsToken(String xboxToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject entity = new JsonObject();
                JsonObject props = new JsonObject();
                JsonArray userTokens = new JsonArray();
                userTokens.add(new JsonPrimitive(xboxToken));
                props.addProperty("SandboxId", "RETAIL");
                props.add("UserTokens", userTokens);
                entity.add("Properties", props);
                entity.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                entity.addProperty("TokenType", "JWT");
                String resp = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", entity.toString());
                JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
                String token = optString(json, "Token");
                if (isBlank(token)) {
                    String err = json.has("XErr") ? json.get("XErr").getAsString() + ": " + optString(json, "Message") : "Missing XSTS token";
                    throw new Exception(err);
                }
                String uhs = json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
                Map<String,String> map = new HashMap<>();
                map.put("Token", token);
                map.put("uhs", uhs);
                return map;
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire XSTS token", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireMCAccessToken(String xstsToken, String userHash, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                // use identityToken
                String identity = String.format("XBL3.0 x=%s;%s", userHash, xstsToken);
                body.addProperty("identityToken", identity);
                String resp = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body.toString());
                JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
                String token = optString(json, "access_token");
                if (isBlank(token)) {
                    String err = json.has("error") ? json.get("error").getAsString() + ": " + optString(json, "errorMessage") : "Missing MC token";
                    throw new Exception(err);
                }
                return token;
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire MC token", e);
            }
        }, executor);
    }

    public static CompletableFuture<AltLoginResult> fetchProfile(String mcToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://api.minecraftservices.com/minecraft/profile");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("Authorization", "Bearer " + mcToken);
                con.setConnectTimeout(30000);
                con.setReadTimeout(30000);
                int code = con.getResponseCode();
                InputStream is = code == 200 ? con.getInputStream() : con.getErrorStream();
                String resp = readAll(is);
                if (code != 200) {
                    JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
                    String err = json.has("error") ? json.get("error").getAsString() + ": " + optString(json, "errorMessage") : "Profile fetch failed " + code;
                    throw new Exception(err);
                }
                JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
                String id = optString(json, "id");
                String name = optString(json, "name");
                if (isBlank(id) || isBlank(name)) throw new Exception("Missing profile id/name");
                return new AltLoginResult(name, id, mcToken);
            } catch (Exception e) {
                throw new CompletionException("Unable to fetch profile", e);
            }
        }, executor);
    }

    // ---- HTTP helpers ----

    private static String postForm(String urlStr, Map<String,String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String,String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty("Content-Length", String.valueOf(data.length));
        con.setConnectTimeout(30000);
        con.setReadTimeout(30000);
        try (OutputStream os = con.getOutputStream()) { os.write(data); }
        int code = con.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        String resp = readAll(is);
        if (resp == null) resp = "";
        // Even on non-200 we return body for error parsing; caller will check tokens
        return resp;
    }

    private static String postJson(String urlStr, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Content-Length", String.valueOf(data.length));
        con.setConnectTimeout(30000);
        con.setReadTimeout(30000);
        try (OutputStream os = con.getOutputStream()) { os.write(data); }
        int code = con.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        String resp = readAll(is);
        if (resp == null) resp = "";
        return resp;
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static Map<String,String> parseQuery(String q) {
        Map<String,String> map = new LinkedHashMap<>();
        if (q == null || q.isEmpty()) return map;
        for (String p : q.split("&")) {
            if (p.isEmpty()) continue;
            String[] kv = p.split("=", 2);
            try {
                String k = URLDecoder.decode(kv[0], "UTF-8");
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "";
                map.put(k, v);
            } catch (Exception ignored) {}
        }
        return map;
    }

    private static String optString(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return "";
        try { return o.get(k).getAsString(); } catch (Exception e) { return ""; }
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static byte[] buildCallbackHtml(boolean success) {
        String html = success
                ? "<html><body style=\"font-family:sans-serif;background:#1a1a1a;color:#aaffaa;text-align:center;padding-top:60px\"><h2>Login successful!</h2><p>You can now close this window and return to the game.</p></body></html>"
                : "<html><body style=\"font-family:sans-serif;background:#1a1a1a;color:#ffaaaa;text-align:center;padding-top:60px\"><h2>Login failed or cancelled</h2><p>Please close this window and try again.</p></body></html>";
        return html.getBytes(StandardCharsets.UTF_8);
    }

    public static class AltLoginResult {
        public final String username;
        public final String uuid;
        public final String mcToken;
        public AltLoginResult(String username, String uuid, String mcToken) {
            this.username = username;
            this.uuid = uuid;
            this.mcToken = mcToken;
        }
    }
}
