module.exports = {
  preset: '@react-native/jest-preset',
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|react-native-safe-area-context|@react-native(-community)?|immer|redux|redux-thunk|reselect|@reduxjs)/)',
  ],
  moduleNameMapper: {
    '^@app/(.*)$': '<rootDir>/src/app/$1',
    '^@features/(.*)$': '<rootDir>/src/features/$1',
    '^@infra/(.*)$': '<rootDir>/src/infrastructure/$1',
    '^@platform/(.*)$': '<rootDir>/src/platform/$1',
    '^@shared/(.*)$': '<rootDir>/src/shared/$1',
    '^@contracts/(.*)$': '<rootDir>/contracts/$1',
    '^@specs/(.*)$': '<rootDir>/specs/$1',
  },
};
