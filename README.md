# k-skill Logent 플러그인

한국인을 위한 스킬 모음집 [NomaDamas/k-skill](https://github.com/NomaDamas/k-skill)을
Logent에 제공하는 동적 플러그인이에요. 원본 저장소를 포크해 `logent` 브랜치에
플러그인 패키징을 얹었어요.

- 플러그인 ID: `com.sleepysoong.logent.k-skill`
- 스킬: upstream 각 스킬 디렉터리의 `skill.json`에서 생성한 카탈로그
- 본문: `npx`로 고정한 `@nomadamas/k-skill@0.11.1` 패키지를 준비하고 npm 진입점을 `node`로 읽어요. 전역 `k-skill` 설치나 Android에 없는 `/usr/bin/env` shebang에 의존하지 않아요.
- 카탈로그는 upstream `*/skill.json`에서 생성하고, 테스트가 생성 결과와 upstream이 같은지 대조해요.

## 실행 환경과 제한

- Termux의 Node.js 22.20 이상과 npx가 필요해요. 없으면 Termux에서 `pkg install nodejs`로 설치해 주세요.
- Logent의 `설정 → Termux → RUN_COMMAND 권한 요청`에서 허용해 주세요. 요청창이 뜨지 않으면 `Logent 권한 설정`으로 이동해요. Termux의 `allow-external-apps=true`와는 별개예요.
- 카탈로그 조회는 Termux 없이 가능하지만 본문 읽기는 Termux 셸과 npm 패키지가 필요해요. 전역 `command -v k-skill` 결과는 플러그인의 준비 상태를 뜻하지 않아요.
- 본문을 읽었다고 외부 도구 실행이나 검색이 성공한 것은 아니에요. 본문 앞에 붙는 Logent 실행 환경 규칙에 따라 모든 브라우저 단계는 정식 `browser_use` 도구로 실행해요.
- `browser-use` 플러그인을 필수 의존성으로 선언해요. `browser-use`가 설치·활성 상태가 아니면 코어가 k-skill 기여를 적재하지 않고 설정에 이유를 표시해요. 두 플러그인과 그 의존성을 지원하는 Logent를 함께 설치해 주세요.
- `bunjang-cli`·Playwright·브라우저 Python helper와 원격 Playwright 서비스는 직접 실행하지 않아요. HTTP 전용 조회와 파일 후처리는 유지하고, 로그인·본인인증·CAPTCHA·전자서명·결제 같은 경계는 자동 우회하지 않아요.

## 카탈로그 갱신

upstream 스킬을 추가하거나 `skill.json`을 바꾼 뒤에는 아래 명령으로 카탈로그와 DEX 메타데이터를
다시 만들어요. 테스트가 upstream `*/skill.json`과 생성 결과가 같은지 확인해요.

```bash
python3 scripts/generate-k-skill-catalog.py
```

## 빌드

```bash
LOGENT_CONTENT_SIGNING_KEY_FILE=/path/to/key.pem \
LOGENT_PLUGIN_PUBLIC_KEY_FILE=/path/to/public.der \
./scripts/build.sh -PlogentPluginApiMavenLocal=true
```

릴리스는 `./scripts/release.sh 1.0.1 'English summary' '한국어 해요체 본문'`으로 수행해요.
`main`·`logent` 커밋마다 `.github/workflows/plugin-edge.yml`이 서명된 `.lpg`를 `edge`에 올려요.

원본 README는 [`README.upstream.md`](README.upstream.md), 출처·변경 내역은 [`NOTICE`](NOTICE),
고정한 upstream 커밋은 [`UPSTREAM_COMMIT`](UPSTREAM_COMMIT)에 있어요.
