module.exports = {
  root: true,
  extends: '@react-native',
  rules: {'no-void': ['error', {allowAsStatement: true}]},
  ignorePatterns: ['src/infrastructure/network/generated/*.js', 'android/**/build/**', 'ios/Pods/**'],
};
