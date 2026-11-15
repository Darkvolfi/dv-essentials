package com.darkvolfi.dvskin.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Fetches pictures for the player's own choice, on a background thread, with time and size limits.
 * Only the client ever downloads; the server just passes on the PNG bytes it is sent.
 */
public final class SkinDownloader {
    /** The picture, and the arm model when the source says (null: unknown). */
    public record Result(byte[] png, Boolean slim) {}

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final long TOTAL_TIMEOUT_MS = 15_000;
    private static final int MAX_JSON = 16 * 1024;
    private static final Pattern MOJANG_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern ELYBY_NAME = Pattern.compile("[A-Za-z0-9_.\\-]{1,32}");

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DV Skin download");
        thread.setDaemon(true);
        return thread;
    });

    private SkinDownloader() {}

    /** {@code kind}: "mojang" or "elyby" (a player name), "url" (a direct PNG link), "file" (a path). */
    public static CompletableFuture<Result> fetch(String kind, String input) {
        String text = input.trim();
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (text.isEmpty()) throw new SkinException("empty");
                return switch (kind) {
                    case "mojang" -> mojang(text);
                    case "elyby" -> elyby(text);
                    case "url" -> new Result(get(link(text), SkinImages.MAX_INPUT), null);
                    case "file" -> new Result(file(text), null);
                    default -> throw new SkinException("invalid");
                };
            } catch (SkinException e) {
                throw new CompletionException(e);
            }
        }, EXECUTOR);
    }

    /** The {@link SkinException} behind a failed fetch, or a generic one. */
    public static SkinException cause(Throwable error) {
        Throwable t = error;
        while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
        return t instanceof SkinException e ? e : new SkinException("network");
    }

    private static byte[] file(String path) throws SkinException {
        try {
            Path p = Path.of(path);
            if (Files.size(p) > SkinImages.MAX_INPUT) throw new SkinException("too_big");
            return Files.readAllBytes(p);
        } catch (IOException | RuntimeException e) {
            throw new SkinException("file");
        }
    }

    private static Result mojang(String name) throws SkinException {
        if (!MOJANG_NAME.matcher(name).matches()) throw new SkinException("not_found");
        JsonObject profile = json("https://api.mojang.com/users/profiles/minecraft/" + name);
        String id = profile.has("id") ? profile.get("id").getAsString() : "";
        if (!id.matches("[0-9a-fA-F]{32}")) throw new SkinException("not_found");
        JsonObject session = json("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
        try {
            for (JsonElement property : session.getAsJsonArray("properties")) {
                JsonObject p = property.getAsJsonObject();
                if (!"textures".equals(p.get("name").getAsString())) continue;
                String decoded = new String(Base64.getDecoder().decode(p.get("value").getAsString()), StandardCharsets.UTF_8);
                return skin(JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures"));
            }
        } catch (RuntimeException e) {
            throw new SkinException("network");
        }
        throw new SkinException("no_skin");
    }

    private static Result elyby(String name) throws SkinException {
        if (!ELYBY_NAME.matcher(name).matches()) throw new SkinException("not_found");
        return skin(json("https://skinsystem.ely.by/textures/" + URLEncoder.encode(name, StandardCharsets.UTF_8)));
    }

    /** Downloads SKIN from a textures object ({"SKIN": {"url": …, "metadata": {"model": "slim"}}}). */
    private static Result skin(JsonObject textures) throws SkinException {
        try {
            if (textures == null || !textures.has("SKIN")) throw new SkinException("no_skin");
            JsonObject skin = textures.getAsJsonObject("SKIN");
            boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                    && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
            return new Result(get(link(skin.get("url").getAsString()), SkinImages.MAX_INPUT), slim);
        } catch (IllegalStateException | ClassCastException | NullPointerException | UnsupportedOperationException e) {
            throw new SkinException("network");
        }
    }

    private static URL link(String text) throws SkinException {
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) throw new SkinException("bad_url");
            // NameMC blocks downloads that don't come from a browser, even for its picture links
            String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
            if (host.equals("namemc.com") || host.endsWith(".namemc.com")) throw new SkinException("namemc");
            return uri.toURL();
        } catch (SkinException e) {
            throw e;
        } catch (Exception e) {
            throw new SkinException("bad_url");
        }
    }

    private static JsonObject json(String url) throws SkinException {
        byte[] body = get(link(url), MAX_JSON);
        try {
            return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new SkinException("network");
        }
    }

    private static byte[] get(URL url, int maxBytes) throws SkinException {
        long deadline = System.currentTimeMillis() + TOTAL_TIMEOUT_MS;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "DV-Skin");
            int code = connection.getResponseCode();
            if (code == 204 || code == 404) throw new SkinException("not_found");
            if (code != 200) throw new SkinException("network");
            if (connection.getContentLengthLong() > maxBytes) throw new SkinException("too_big");
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    if (out.size() > maxBytes) throw new SkinException("too_big");
                    if (System.currentTimeMillis() > deadline) throw new SkinException("timeout");
                }
                return out.toByteArray();
            }
        } catch (SkinException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new SkinException("timeout");
        } catch (IOException | RuntimeException e) {
            throw new SkinException("network");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
