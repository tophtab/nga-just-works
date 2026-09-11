package sp.phone.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Incremental UTF-8/SSE framing for one OpenAI-compatible completion, independent of Android. */
final class AiStreamParser {
    // Matches the shared JSON decoder's character bound; transport and channel limits differ.
    static final int MAX_EVENT_CHARS = 512 * 1024;

    private final AiMessageAccumulator message;
    private final StringBuilder line = new StringBuilder();
    private final StringBuilder data = new StringBuilder();
    private String event = "";
    private int eventChars;
    private boolean hasData;
    private boolean skipLf;
    private boolean firstCharacter = true;
    private boolean complete;
    private String answer;

    private AiStreamParser(AiResponseParser.ProgressListener listener) {
        message = new AiMessageAccumulator(listener);
    }

    static String read(InputStream input, AiResponseParser.ProgressListener listener)
            throws IOException, AiResponseParser.InvalidResponseException {
        AiStreamParser parser = new AiStreamParser(listener);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer bytes = ByteBuffer.allocate(8192);
        bytes.limit(0);
        CharBuffer characters = CharBuffer.allocate(2048);
        boolean eof = false;
        try (InputStream stream = input) {
            while (true) {
                CoderResult decoded = decoder.decode(bytes, characters, eof);
                characters.flip();
                // A bulk Reader can throw after decoding a valid prefix without returning its
                // length. Consume that prefix first so malformed later bytes cannot erase it.
                while (characters.hasRemaining()) {
                    parser.accept(characters.get());
                    if (parser.complete) {
                        return parser.answer;
                    }
                }
                characters.clear();
                if (decoded.isError()) {
                    decoded.throwException();
                }
                if (decoded.isOverflow()) {
                    continue;
                }
                if (eof) {
                    // SSE dispatch needs the blank line. An unfinished frame is not completion.
                    throw new AiResponseParser.InvalidResponseException(AiError.INTERRUPTED_RESPONSE);
                }
                bytes.compact();
                int count = stream.read(bytes.array(), bytes.position(), bytes.remaining());
                if (count == -1) {
                    eof = true;
                } else {
                    bytes.position(bytes.position() + count);
                }
                bytes.flip();
            }
        } finally {
            parser.message.finishPartial();
        }
    }

    private void accept(char character) throws AiResponseParser.InvalidResponseException {
        if (firstCharacter) {
            firstCharacter = false;
            if (character == '\ufeff') {
                return;
            }
        }
        if (skipLf) {
            skipLf = false;
            if (character == '\n') {
                return;
            }
        }
        if (++eventChars > MAX_EVENT_CHARS) {
            throw new AiResponseParser.InvalidResponseException(AiError.RESPONSE_TOO_LARGE);
        }
        if (character == '\r' || character == '\n') {
            consumeLine();
            skipLf = character == '\r';
        } else {
            line.append(character);
        }
    }

    private void consumeLine() throws AiResponseParser.InvalidResponseException {
        if (line.length() == 0) {
            dispatch();
            data.setLength(0);
            hasData = false;
            event = "";
            eventChars = 0;
            return;
        }
        String value = line.toString();
        line.setLength(0);
        if (value.charAt(0) == ':') {
            return;
        }
        int colon = value.indexOf(':');
        String field = colon < 0 ? value : value.substring(0, colon);
        String payload = colon < 0 ? "" : value.substring(colon + 1);
        if (payload.startsWith(" ")) {
            payload = payload.substring(1);
        }
        if ("data".equals(field)) {
            if (hasData) {
                data.append('\n');
            }
            hasData = true;
            data.append(payload);
        } else if ("event".equals(field)) {
            event = payload;
        }
        // id, retry, comments, and unknown SSE fields carry no answer/reasoning content.
    }

    private void dispatch() throws AiResponseParser.InvalidResponseException {
        if ("error".equals(event)) {
            throw new AiResponseParser.InvalidResponseException(AiError.SERVER);
        }
        if (!hasData || data.toString().trim().isEmpty()) {
            return;
        }
        String payload = data.toString().trim();
        if ("[DONE]".equals(payload)) {
            answer = message.complete(null);
            complete = true;
            return;
        }
        AiResponseParser.Chunk chunk = AiResponseParser.chatChunk(payload, true);
        message.append(chunk.content, chunk.reasoning);
        if (chunk.finishReason != null) {
            answer = message.complete(chunk.finishReason);
            complete = true;
        }
    }
}
