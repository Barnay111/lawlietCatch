const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const config = getDefaultConfig(__dirname);

// SDK лежит рядом с приложением (../sdk) и подключён через file:.
// Metro должен видеть эту папку и брать react / react-native только из приложения,
// иначе в бандл попадут две копии React Native.
const sdkRoot = path.resolve(__dirname, '../sdk');
config.watchFolders = [sdkRoot];
config.resolver.nodeModulesPaths = [path.resolve(__dirname, 'node_modules')];

module.exports = config;
