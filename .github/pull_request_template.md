## Summary



## Checklist

- [ ] `mvn -f backend/pom.xml clean test` — all pass
- [ ] `npm --prefix frontend run check` — pass
- [ ] `npm --prefix frontend run build` — pass
- [ ] `npm --prefix frontend run verify:production` — pass
- [ ] `npm --prefix frontend run test:smoke` — pass
- [ ] `npm --prefix frontend run verify:byok` — pass
- [ ] `npm --prefix frontend run verify:dark` — pass
- [ ] `npm --prefix frontend run verify:a11y` — pass
- [ ] `npm --prefix frontend run verify:visual` — pass
- [ ] `npm --prefix frontend audit --omit=dev` — clean
- [ ] `git diff --check` — clean
