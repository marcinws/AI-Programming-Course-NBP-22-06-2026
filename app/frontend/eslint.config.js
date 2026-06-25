// @ts-check
const eslint = require('@eslint/js');
const globals = require('globals');
const tsPlugin = require('@typescript-eslint/eslint-plugin');
const tsParser = require('@typescript-eslint/parser');
const angularPlugin = require('@angular-eslint/eslint-plugin');
const angularTemplatePlugin = require('@angular-eslint/eslint-plugin-template');
const templateParser = require('@angular-eslint/template-parser');

/**
 * ESLint flat config for the Angular 20 frontend.
 * Uses ESLint 9 flat config format (eslint.config.js).
 */
module.exports = [
  // --- TypeScript source files (non-spec) ---
  {
    files: ['src/**/*.ts'],
    ignores: ['src/**/*.spec.ts'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        project: ['tsconfig.app.json'],
        tsconfigRootDir: __dirname,
      },
      globals: {
        ...globals.browser,
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      '@angular-eslint': angularPlugin,
    },
    rules: {
      // ESLint core recommended
      ...eslint.configs.recommended.rules,
      // TypeScript recommended (flat config variant)
      ...tsPlugin.configs['flat/recommended'].rules,
      // Angular recommended
      ...angularPlugin.configs['recommended'].rules,

      // Angular selector conventions
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],

      // Allow unused parameters/variables in interface/type signatures and
      // callbacks that start with _ (convention for intentionally unused args).
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          ignoreRestSiblings: true,
          args: 'after-used',
          caughtErrors: 'all',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
      'no-unused-vars': 'off', // handled by @typescript-eslint/no-unused-vars
    },
  },

  // --- TypeScript spec files ---
  {
    files: ['src/**/*.spec.ts'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        project: ['tsconfig.spec.json'],
        tsconfigRootDir: __dirname,
      },
      globals: {
        ...globals.browser,
        ...globals.jasmine,
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
    },
    rules: {
      // ESLint core recommended
      ...eslint.configs.recommended.rules,
      // TypeScript recommended (flat config variant)
      ...tsPlugin.configs['flat/recommended'].rules,
      // Disable rules that are too strict for test files
      '@typescript-eslint/no-explicit-any': 'warn',
      // Allow intentionally unused params prefixed with _ in interface signatures
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          ignoreRestSiblings: true,
          args: 'after-used',
          caughtErrors: 'all',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
      'no-unused-vars': 'off',
    },
  },

  // --- Angular inline templates (processed from .ts files) ---
  {
    files: ['src/**/*.ts'],
    ignores: ['src/**/*.spec.ts'],
    plugins: {
      '@angular-eslint/template': angularTemplatePlugin,
    },
    processor: angularTemplatePlugin.configs['process-inline-templates'].processor,
  },

  // --- Angular HTML templates ---
  {
    files: ['src/**/*.html'],
    languageOptions: {
      parser: templateParser,
    },
    plugins: {
      '@angular-eslint/template': angularTemplatePlugin,
    },
    rules: {
      ...angularTemplatePlugin.configs['recommended'].rules,
    },
  },

  // --- Ignore build output and node_modules ---
  {
    ignores: ['dist/', 'node_modules/', '.angular/', 'coverage/'],
  },
];
