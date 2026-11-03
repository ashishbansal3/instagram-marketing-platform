import type { Config } from "tailwindcss";

export default {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        display: ["var(--font-display)", "serif"],
        body: ["var(--font-body)", "system-ui", "sans-serif"],
      },
      colors: {
        brand: {
          DEFAULT: "#0b5c54",
          deep: "#073f3a",
        },
        accent: {
          DEFAULT: "#d97706",
          soft: "#fde68a",
        },
      },
    },
  },
  plugins: [],
} satisfies Config;
