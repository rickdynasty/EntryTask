const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

// Keep the project root: src/, specs/, contracts/ and mock/ share one module graph.
// JSON remains a source module; Android consumes mock/ through packaged assets.
module.exports = mergeConfig(getDefaultConfig(__dirname), {
  server: {
    // Support tooling that still requests the conventional /index.bundle URL.
    // Native development and release entry paths are explicitly src/index.
    rewriteRequestUrl: url =>
      url.replace(
        /^(https?:\/\/[^/]+)?\/index\.bundle(?=\?|$)/,
        '$1/src/index.bundle',
      ),
  },
});
