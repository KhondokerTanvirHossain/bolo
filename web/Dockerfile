# Built outside Docker (pnpm build → .next/standalone); the standalone output is plain JS, so it runs on amd64 as is.
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production HOSTNAME=0.0.0.0 PORT=3000 NEXT_TELEMETRY_DISABLED=1
COPY .next/standalone ./
COPY .next/static ./.next/static
COPY public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
