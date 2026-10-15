-- Run against a target X APK. method_kind excludes methods that are only references.
SELECT DISTINCT descriptor, method_kind FROM methods
WHERE class_descriptor = 'Lokhttp3/internal/connection/RealCall;'
  AND name = 'getResponseWithInterceptorChain$okhttp' AND method_kind IS NOT NULL
ORDER BY descriptor;

SELECT DISTINCT method FROM method_calls
WHERE callee = 'Lokhttp3/internal/connection/RealCall;->getResponseWithInterceptorChain$okhttp()Lokhttp3/Response;'
ORDER BY method;

-- Run against an XBlocker APK. Check cleaner calls and inspect the method with decode.
SELECT DISTINCT method, callee FROM method_calls
WHERE method LIKE 'Lio/github/xblocker/core/RuleParser;->clean%'
ORDER BY method, callee;
