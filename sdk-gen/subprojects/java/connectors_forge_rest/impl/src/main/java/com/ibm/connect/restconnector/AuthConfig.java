/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.Collections;
import java.util.List;

/**
 * Holds the authentication configuration parsed from the {@code $authentication} DSL section.
 *
 * <p>All authentication types — including built-in ones and {@code custom} — use the same
 * uniform {@code headers} array structure. Each entry declares one connection property that
 * the user must supply, and describes how to place its value into an HTTP request header.
 *
 * <pre>
 * // api_key
 * "$authentication": {
 *   "type": "api_key",
 *   "headers": [
 *     { "name": "api_key", "label": "API Key", "description": "Your API key",
 *       "masked": true, "header": "Authorization", "value": "ApiKey $api_key" }
 *   ]
 * }
 *
 * // oauth2
 * "$authentication": {
 *   "type": "oauth2",
 *   "headers": [
 *     { "name": "bearer_token", "label": "Bearer Token", "description": "OAuth 2.0 access token",
 *       "masked": true, "header": "Authorization", "value": "Bearer $bearer_token" }
 *   ]
 * }
 *
 * // basic — engine applies base64 when it sees the base64(...) pattern in value
 * "$authentication": {
 *   "type": "basic",
 *   "headers": [
 *     { "name": "username", "label": "Username", "description": "", "masked": false,
 *       "header": "Authorization", "value": "Basic base64($username:$password)" },
 *     { "name": "password", "label": "Password", "description": "", "masked": true,
 *       "header": null, "value": null }
 *   ]
 * }
 *
 * // custom — arbitrary headers
 * "$authentication": {
 *   "type": "custom",
 *   "headers": [
 *     { "name": "x_api_key", "label": "API Key",   "description": "", "masked": true,
 *       "header": "X-API-Key",   "value": "$x_api_key" },
 *     { "name": "x_tenant",  "label": "Tenant ID", "description": "", "masked": false,
 *       "header": "X-Tenant-ID", "value": "$x_tenant" }
 *   ]
 * }
 *
 * // none
 * "$authentication": { "type": "none" }
 * </pre>
 *
 * <p><b>Value template syntax</b><br>
 * The {@code value} string may contain {@code $name} placeholders that are replaced at runtime
 * with the corresponding connection-property values.  The special form
 * {@code base64(expr)} causes the engine to base64-encode the result of {@code expr} before
 * inserting it — this is used for HTTP Basic authentication.
 *
 * <p>A {@code null} {@code header} or {@code value} marks a UI-only credential field
 * (e.g. the {@code password} entry in {@code basic}) that is consumed inside a template
 * of another header and does not produce a header by itself.
 */
public class AuthConfig
{
    private final AuthenticationType type;
    private final List<HeaderDef>    headers;

    /**
     * Creates an {@link AuthConfig} for {@link AuthenticationType#NONE}.
     */
    public AuthConfig()
    {
        this.type    = AuthenticationType.NONE;
        this.headers = Collections.emptyList();
    }

    /**
     * Creates an {@link AuthConfig} with an explicit type and header definitions.
     *
     * @param type
     *            the authentication type
     * @param headers
     *            the ordered list of header / credential definitions
     */
    public AuthConfig(AuthenticationType type, List<HeaderDef> headers)
    {
        this.type    = type;
        this.headers = headers != null ? Collections.unmodifiableList(headers) : Collections.emptyList();
    }

    /** Returns the authentication type. */
    public AuthenticationType getType()    { return type;    }

    /** Returns the ordered list of header definitions (may be empty for {@code none}). */
    public List<HeaderDef>    getHeaders() { return headers; }

    // -------------------------------------------------------------------------

    /**
     * Describes one connection-property field and (optionally) the HTTP header it produces.
     *
     * <ul>
     *   <li>{@code name}        – connection-property key; used both as the UI field identifier
     *                             and as the {@code $name} placeholder in value templates.</li>
     *   <li>{@code label}       – human-readable label shown in the CP4D UI.</li>
     *   <li>{@code description} – optional hint text shown under the field in the CP4D UI.</li>
     *   <li>{@code masked}      – {@code true} if the value should be shown as password dots.</li>
     *   <li>{@code header}      – HTTP header name written into the request, e.g.
     *                             {@code "Authorization"} or {@code "X-API-Key"}.
     *                             {@code null} for UI-only fields that are referenced inside
     *                             another entry's value template (e.g. {@code password} in basic).</li>
     *   <li>{@code value}       – template string placed as the header value, e.g.
     *                             {@code "ApiKey $api_key"} or
     *                             {@code "Basic base64($username:$password)"}.
     *                             {@code null} for UI-only fields.</li>
     * </ul>
     */
    public static class HeaderDef
    {
        private final String  name;
        private final String  label;
        private final String  description;
        private final boolean masked;
        private final String  header;
        private final String  value;

        public HeaderDef(String name, String label, String description,
                boolean masked, String header, String value)
        {
            this.name        = name;
            this.label       = label;
            this.description = description != null ? description : "";
            this.masked      = masked;
            this.header      = header;
            this.value       = value;
        }

        public String  getName()        { return name;        }
        public String  getLabel()       { return label;       }
        public String  getDescription() { return description; }
        public boolean isMasked()       { return masked;      }
        /** HTTP header name, or {@code null} for a UI-only credential field. */
        public String  getHeader()      { return header;      }
        /** Value template, or {@code null} for a UI-only credential field. */
        public String  getValue()       { return value;       }
    }
}
