# k-skill Logent 플러그인

한국인을 위한 스킬 모음집 [NomaDamas/k-skill](https://github.com/NomaDamas/k-skill)을
Logent에 제공하는 동적 플러그인이에요. 원본 저장소를 포크해 `logent` 브랜치에
플러그인 패키징을 얹었어요.

- 플러그인 ID: `com.sleepysoong.logent.k-skill`
- 스킬: upstream 각 스킬 디렉터리의 `skill.json`에서 생성한 카탈로그
- 본문: `npx --yes @nomadamas/k-skill@0.11.1 instruct <name>`을 Termux 셸로 실행해 읽어요.
- 카탈로그는 upstream `*/skill.json`에서 생성하고, 테스트가 생성 결과와 upstream이 같은지 대조해요.

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
