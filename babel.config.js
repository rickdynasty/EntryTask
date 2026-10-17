module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [
    [
      'module-resolver',
      {
        root: ['./'],
        alias: {
          '@app': './src/app',
          '@features': './src/features',
          '@infra': './src/infrastructure',
          '@platform': './src/platform',
          '@shared': './src/shared',
          '@contracts': './contracts',
          '@specs': './specs',
        },
      },
    ],
  ],
};
