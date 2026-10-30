import { FlatCompat } from "@eslint/eslintrc";

const compat = new FlatCompat({
  baseDirectory: import.meta.dirname
});

const config = [
  ...compat.extends("next/core-web-vitals", "next/typescript"),
  {
    rules: {
      // Runtime images come from the Java backend, so the optimizer cannot be whitelisted at build time.
      "@next/next/no-img-element": "off"
    }
  },
  {
    ignores: [".next/**", "node_modules/**", "coverage/**", "next-env.d.ts"]
  }
];

export default config;
