/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * File utilities.
 */
public class FileUtils
{
    /**
     * File format avro.
     */
    public static final String FILE_FORMAT_AVRO = "avro";

    /**
     * File format csv.
     */
    public static final String FILE_FORMAT_CSV = "csv";

    /**
     * File format delimited (text).
     */
    public static final String FILE_FORMAT_DELIMITED = "delimited";

    /**
     * File format json.
     */
    public static final String FILE_FORMAT_JSON = "json";

    /**
     * File format orc.
     */
    public static final String FILE_FORMAT_ORC = "orc";

    /**
     * File format parquet.
     */
    public static final String FILE_FORMAT_PARQUET = "parquet";

    /**
     * File format xml.
     */
    public static final String FILE_FORMAT_XML = "xml";

    private static final Logger LOGGER = getLogger(FileUtils.class);

    private static final int STREAM_BUFFER_SIZE = 1025 * 1024;
    private static final int MAX_FILE_FORMAT_HEADER_SIZE = 4096;
    private static final int MAX_COLUMN_HEADERS_SIZE = 65536;

    private static final MimeTypes MIMETYPES = MimeTypes.getDefaultMimeTypes();

    private static final Set<String> FORMATS_WITH_HEADER
            = ImmutableSet.of(FILE_FORMAT_AVRO, FILE_FORMAT_JSON, FILE_FORMAT_ORC, FILE_FORMAT_PARQUET);

    private static final ImmutableMultimap<String,
            String> FORMAT_EXTENSION_MAP = ImmutableMultimap.<String, String>builder().put(FILE_FORMAT_AVRO, ".avro")
                    .put(FILE_FORMAT_CSV, ".csv").put(FILE_FORMAT_DELIMITED, ".txt").put(FILE_FORMAT_JSON, ".json")
                    .put(FILE_FORMAT_ORC, ".orc").put(FILE_FORMAT_PARQUET, ".parquet").put(FILE_FORMAT_XML, ".xml").build();

    private FileUtils()
    {
        // prevent instantiation
    }

    /**
     * Returns a filename extension for the given file format.
     *
     * @param fileFormat
     * @return a filename extension for the given file format
     */
    public static String getFilenameExtension(String fileFormat)
    {
        return FORMAT_EXTENSION_MAP.get(fileFormat).iterator().next();
    }

    /**
     * Ensure that {@link InputStream#mark} is supported.
     *
     * @param inputStream
     * @return An InputStream that supports {@link InputStream#mark}
     */
    public static InputStream ensureMarkSupported(InputStream inputStream)
    {
        if (!inputStream.markSupported()) {
            return new BufferedInputStream(inputStream, STREAM_BUFFER_SIZE);
        }
        return inputStream;
    }

    /**
     * @param stream
     *            the InputStream to read. The mark method must be supported.
     * @param filename
     *            the name of the file being processed.
     * @return the MediaType for the stream or application/octet-stream if it cannot
     *         be determined.
     * @throws IOException
     */
    @SuppressWarnings("PMD.CloseResource")
    public static String detectMimeType(InputStream stream, String filename) throws IOException
    {
        final TikaInputStream tis = TikaInputStream.get(stream);
        final Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        return MIMETYPES.detect(tis, metadata).getBaseType().toString();
    }

    /**
     * Determine the file format by looking at the file content.
     *
     * @param mimeType
     *            the file mime type
     * @param fileName
     *            the file name or full pathname used when checking file format by
     *            extension
     * @param stream
     *            An InputStream used to process the first few characters of the
     *            file. The InputStream must support the mark function.
     * @return the name of the file type, empty files are returned as 'delimited'
     */
    public static String detectFileFormat(String mimeType, String fileName, InputStream stream)
    {
        switch (mimeType) {
        case "application/json":
            return FILE_FORMAT_JSON;
        case "application/xml":
        case "text/xml":
            return FILE_FORMAT_XML;
        case "text/csv":
        case "text/tab-separated-values":
            return (fileName != null && fileName.endsWith(".csv")) ? FILE_FORMAT_CSV : FILE_FORMAT_DELIMITED;
        default:
            final String detectedFileFormat = detectFileFormatFromHeader(stream);
            return detectedFileFormat != null ? detectedFileFormat : detectFileFormatFromName(fileName, FORMATS_WITH_HEADER);
        }
    }

    private static String detectFileFormatFromHeader(InputStream is)
    {
        final byte[] fileContent = new byte[MAX_FILE_FORMAT_HEADER_SIZE];
        final int length = peekBytes(is, fileContent);

        final String fileFormat = detectMagicNumber(fileContent, length);
        if (fileFormat != null) {
            return fileFormat;
        }

        // No magic header, check for JSON.
        return detectJSON(fileContent, length);
    }

    /**
     * Reads an input stream to the length of the given buffer and then resets the
     * stream.
     *
     * @param is
     * @param fileContent
     * @return the number of bytes returned in the given byte array.
     */
    public static int peekBytes(InputStream is, byte[] fileContent)
    {
        int length = 0;
        is.mark(fileContent.length);
        try {
            try {
                length = is.read(fileContent);
            }
            finally {
                is.reset();
            }
        }
        catch (final IOException e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return length;
    }

    private static String detectMagicNumber(byte[] fileContent, int length)
    {
        // ORC has 3-byte magic number:
        if (length >= 3 && fileContent[0] == 'O' && fileContent[1] == 'R' && fileContent[2] == 'C') {
            return FILE_FORMAT_ORC;
        }

        // Parquet and Avro have 4-byte magic numbers:
        if (length >= 4) {
            if (fileContent[0] == 'P' && fileContent[1] == 'A' && fileContent[2] == 'R' && fileContent[3] == '1') {
                return FILE_FORMAT_PARQUET;
            }
            if (fileContent[0] == 'O' && fileContent[1] == 'b' && fileContent[2] == 'j' && fileContent[3] == 0x01) {
                return FILE_FORMAT_AVRO;
            }
        }

        return null;
    }

    private static String detectJSON(byte[] fileContent, int length)
    {
        for (int i = 0; i < length; i++) {
            if (Character.isWhitespace(fileContent[i])) {
                // keep looking
                continue;
            }
            if (fileContent[i] == '[' || fileContent[i] == '{') {
                // it appears it could be a JSON Array or element
                return FILE_FORMAT_JSON;
            }
            // cannot be JSON
            break; // NOPMD
        }
        return null;
    }

    private static String detectFileFormatFromName(String fileName, Set<String> excludedFormats)
    {
        return FORMAT_EXTENSION_MAP.entries().stream().filter(e -> !excludedFormats.contains(e.getKey()) && fileName.endsWith(e.getValue()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /**
     * Creates a temporary file for the given input stream.
     *
     * @param inputStream
     * @param fileName
     * @param fileFormat
     * @return the name of the temporary file
     * @throws IOException
     */
    public static String createTempFile(InputStream inputStream, String fileName, String fileFormat) throws IOException
    {
        final Path tempPath = Files.createTempFile(fileName, getFilenameExtension(fileFormat)).toAbsolutePath();
        try (OutputStream outputStream = Files.newOutputStream(tempPath)) {
            ByteStreams.copy(inputStream, outputStream);
        }
        return tempPath.toString();
    }

    /**
     * Deletes the given temporary file.
     *
     * @param tempFilename
     */
    public static void deleteTempFile(String tempFilename)
    {
        try {
            Files.deleteIfExists(Paths.get(tempFilename));
        }
        catch (final Exception e) {
            LOGGER.warn(e.getMessage(), e);
            new File(tempFilename).deleteOnExit();
        }
    }

    /**
     * Detect delimited file properties.
     *
     * @param inputStream
     * @param interactionProperties
     */
    public static void detectDelimitedProperties(InputStream inputStream, DiscoveredAssetInteractionProperties interactionProperties)
    {
        // Find out what properties are already set that we don't need to infer.
        final Properties properties = ModelMapper.toProperties(interactionProperties);
        final String commentCharacterValue = properties.getProperty("comment_character_value");
        final String fieldDelimiterValue = properties.getProperty("field_delimiter_value");
        final String firstLineHeaderValue = properties.getProperty("first_line_header");
        final String quoteCharacterValue = properties.getProperty("quote_character_value");
        final String rowDelimiterValue = properties.getProperty("row_delimiter_value");
        final char commentCharacter
                = commentCharacterValue != null && !commentCharacterValue.isEmpty() ? commentCharacterValue.charAt(0) : '~';
        final char fieldDelimiterCharacter
                = fieldDelimiterValue != null && !fieldDelimiterValue.isEmpty() ? fieldDelimiterValue.charAt(0) : '\0';

        // Peek at the beginning of the file.
        final byte[] fileContent = new byte[MAX_COLUMN_HEADERS_SIZE];
        final int length = peekBytes(inputStream, fileContent);

        // Begin with the assumption that the first line is a header, but we will change
        // that if the contents of a column only contains characters that appear in
        // numbers or datetimes.
        boolean firstLineHeader = firstLineHeaderValue != null ? Boolean.valueOf(firstLineHeaderValue) : true;
        boolean startOfLine = true;
        boolean emptyLine = true;
        boolean startOfField = true;
        boolean inComment = false;
        for (int i = 0; i < length; i++) {
            final char c = (char) fileContent[i];
            if (c == '\n') {
                if (rowDelimiterValue == null) {
                    interactionProperties.put("row_delimiter_value", "\n");
                }
                if (!inComment && !emptyLine) {
                    break;
                }
                startOfLine = true;
                emptyLine = true;
                startOfField = true;
                inComment = false;
            } else if (c == '\r') {
                if (i + 1 < length) {
                    final char nextChar = (char) fileContent[i + 1];
                    if (nextChar == '\n') {
                        i++;
                        if (rowDelimiterValue == null) {
                            interactionProperties.put("row_delimiter_value", "\r\n");
                        }
                    } else if (rowDelimiterValue == null) {
                        interactionProperties.put("row_delimiter_value", "\r");
                    }
                }
                if (!inComment && !emptyLine) {
                    break;
                }
                startOfLine = true;
                emptyLine = true;
                startOfField = true;
                inComment = false;
            } else if (startOfLine && c == commentCharacter) {
                inComment = true;
                startOfLine = false;
                startOfField = false;
                if (commentCharacterValue == null || commentCharacterValue.isEmpty()) {
                    interactionProperties.put("comment_character_value", "~");
                }
            } else {
                startOfLine = false;
                if (!inComment && c != ' ') {
                    emptyLine = false;
                    if (c == '"') {
                        if (quoteCharacterValue == null) {
                            interactionProperties.put("quote_character_value", "\"");
                        }
                    } else if (c == '\'') {
                        if (quoteCharacterValue == null) {
                            interactionProperties.put("quote_character_value", "'");
                        }
                    } else if (fieldDelimiterValue != null && c == fieldDelimiterCharacter) {
                        startOfField = true;
                    } else if (fieldDelimiterValue == null && c == ',') {
                        interactionProperties.put("field_delimiter_value", ",");
                        startOfField = true;
                    } else if (fieldDelimiterValue == null && c == '\t') {
                        interactionProperties.put("field_delimiter_value", "\t");
                        startOfField = true;
                    } else if (fieldDelimiterValue == null && c == '|') {
                        interactionProperties.put("field_delimiter_value", "|");
                        startOfField = true;
                    } else if (startOfField && (c == '+' || c == '-' || c == '.' || (c >= '0' && c <= '9'))) {
                        firstLineHeader = false;
                    } else {
                        startOfField = false;
                    }
                }
            }
        }
        if (firstLineHeaderValue == null) {
            interactionProperties.put("first_line_header", String.valueOf(firstLineHeader));
        }
    }

}
