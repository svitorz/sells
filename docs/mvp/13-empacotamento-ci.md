# Passo 13: Empacotamento portátil, smoke test e CI

## Objetivo

Gerar, no GitHub Actions, uma pasta portátil para Windows e para Linux, com o Java e o PostgreSQL embutidos, e validar automaticamente que ela inicia o banco e aplica as migrations nos dois sistemas operacionais. Resolve D14 e cumpre os critérios da ADR-0012.

## Pré-requisitos

Passo 12 concluído. O repositório precisa estar no GitHub para o CI rodar. Este passo não faz push: o push é decisão da pessoa responsável.

## Decisão técnica

A distribuição do MVP é uma **pasta portátil** gerada por `jpackage --type app-image`, compactada em `.zip` (Windows) ou `.tar.gz` (Linux). O usuário descompacta e abre `Sells.exe` ou `Sells/bin/Sells`. Instaladores `.msi` e `.deb` dependem de ferramentas extras (WiX no Windows) e ficam para depois do MVP. A ADR-0012 é atualizada na tarefa 13.6.

## Tarefas

### 13.1 Smoke test

Crie `src/main/java/com/github/svitorz/sells/SmokeTest.java`:

```java
package com.github.svitorz.sells;

import com.github.svitorz.sells.application.user.UserService;
import com.github.svitorz.sells.infrastructure.database.DatabaseBootstrap;
import com.github.svitorz.sells.infrastructure.database.DatabaseSettings;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Headless check used by CI on the packaged application: starts the embedded
 * database in a temporary directory, runs the migrations, builds the Spring
 * context and checks the pt-BR locale data. Exit code 0 means success.
 */
final class SmokeTest {

    private SmokeTest() {
    }

    static int run() {
        try {
            String money = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(new BigDecimal("1234.5"));
            if (!money.contains("R$") || !money.endsWith("1.234,50")) {
                throw new IllegalStateException("pt-BR locale data is missing: " + money);
            }
            Path dataDir = Files.createTempDirectory("sells-smoke");
            DatabaseSettings settings = new DatabaseSettings(DatabaseSettings.Mode.EMBEDDED, dataDir, null, null, null);
            try (DatabaseBootstrap database = DatabaseBootstrap.start(settings);
                 ConfigurableApplicationContext context = new SpringApplicationBuilder(SellsApplication.class)
                         .headless(true)
                         .web(WebApplicationType.NONE)
                         .properties(database.springProperties())
                         .run()) {
                boolean needsAdmin = context.getBean(UserService.class).needsFirstAdmin();
                if (!needsAdmin) {
                    throw new IllegalStateException("A fresh database must ask for the first administrator");
                }
            }
            System.out.println("SMOKE TEST OK");
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }
}
```

Atualize o `Launcher.main`:

```java
public static void main(String[] args) {
    if (Arrays.asList(args).contains("--smoke-test")) {
        System.exit(SmokeTest.run());
    }
    Application.launch(SellsFxApplication.class, args);
}
```

### 13.2 `pom.xml`: jar comum com classpath e dependências copiadas

Dentro de `<build><plugins>`, acrescente:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <configuration>
        <archive>
            <manifest>
                <mainClass>com.github.svitorz.sells.Launcher</mainClass>
                <addClasspath>true</addClasspath>
                <classpathPrefix>lib/</classpathPrefix>
            </manifest>
        </archive>
    </configuration>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <id>copy-runtime-dependencies</id>
            <phase>package</phase>
            <goals>
                <goal>copy-dependencies</goal>
            </goals>
            <configuration>
                <includeScope>runtime</includeScope>
                <outputDirectory>${project.build.directory}/app/lib</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

As versões dos dois plugins vêm do `spring-boot-starter-parent`. Não as declare.

Depois de `./mvnw -B package`, confirme que `target/app/lib/` contém, entre outros, `javafx-graphics-21.0.6-<plataforma>.jar`, `postgresql-*.jar` e `embedded-postgres-binaries-<plataforma>-amd64-16.15.0.jar`.

### 13.3 Licenças e instruções para o usuário

1. Crie `packaging/licenses/POSTGRESQL-LICENSE.txt` com o texto **oficial e completo** da PostgreSQL License, copiado de <https://www.postgresql.org/about/licence/>. Não resuma nem altere o texto (ADR-0003, seção "Atribuição").
2. Crie `packaging/LEIA-ME.txt` (UTF-8):

```text
Sells - Sistema de vendas livre (GPLv3)

COMO ABRIR
  Windows: abra a pasta "Sells" e dê um duplo clique em "Sells.exe".
  Linux:   execute "Sells/bin/Sells".

  Na primeira vez, o sistema pede o cadastro do administrador.

ONDE FICAM OS DADOS
  Na pasta ".sells" dentro da pasta do seu usuário
  (Windows: C:\Users\<seu usuário>\.sells).
  Não apague essa pasta: ela contém todas as vendas e cadastros.

BACKUP
  Use o menu "Backup" (administrador) com frequência e guarde
  o arquivo gerado fora deste computador.

ATUALIZAR PARA UMA NOVA VERSÃO
  1. Faça um backup pelo menu "Backup".
  2. Feche o Sells.
  3. Substitua a pasta "Sells" pela da nova versão.
  Os dados em ".sells" são mantidos.

CÓDIGO-FONTE E LICENÇA
  https://github.com/svitorz/sells
  As licenças estão na pasta "licenses".
```

### 13.4 Script de empacotamento

Crie `packaging/build-app-image.sh`. Ele roda no Linux e no Git Bash do Windows.

```bash
#!/usr/bin/env bash
# Builds the portable application image with jpackage.
# Usage: ./mvnw -B package && packaging/build-app-image.sh
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(./mvnw -q -B help:evaluate -Dexpression=project.version -DforceStdout)"
APP_VERSION="${VERSION%-SNAPSHOT}"

case "$(uname -s)" in
    Linux*) PLATFORM=linux ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM=windows ;;
    *) echo "Unsupported platform: $(uname -s)" >&2; exit 1 ;;
esac

APP_DIR=target/app
DIST_DIR=target/dist

if [ ! -d "$APP_DIR/lib" ]; then
    echo "Run ./mvnw -B package first" >&2
    exit 1
fi

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR" "$APP_DIR/licenses"
cp "target/sells-$VERSION.jar" "$APP_DIR/sells.jar"
cp LICENSE "$APP_DIR/licenses/LICENSE-Sells.txt"
cp packaging/licenses/*.txt "$APP_DIR/licenses/"
cp packaging/LEIA-ME.txt "$APP_DIR/LEIA-ME.txt"

# Runtime modules. If the smoke test fails with NoClassDefFoundError for a
# java.* or jdk.* class, add the module that contains it here.
MODULES="java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,\
java.net.http,java.prefs,java.scripting,java.security.jgss,java.security.sasl,java.sql,\
java.transaction.xa,java.xml,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.management,\
jdk.net,jdk.unsupported,jdk.zipfs"

jpackage \
    --type app-image \
    --name Sells \
    --app-version "$APP_VERSION" \
    --vendor "Sells contributors" \
    --description "Sistema de vendas livre para pequenos comércios" \
    --input "$APP_DIR" \
    --main-jar sells.jar \
    --main-class com.github.svitorz.sells.Launcher \
    --add-modules "$MODULES" \
    --jlink-options "--strip-debug --no-man-pages --no-header-files" \
    --java-options "-Xmx512m" \
    --dest "$DIST_DIR"

echo "Application image for $PLATFORM created in $DIST_DIR/Sells"
```

Deixe o script executável e com final de linha LF:

1. `git update-index --chmod=+x packaging/build-app-image.sh` (depois de `git add`).
2. Em `.gitattributes`, acrescente a linha `*.sh text eol=lf`.

### 13.5 GitHub Actions

Crie `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
    tags: ["v*"]
  pull_request:

permissions:
  contents: read

jobs:
  build:
    name: Build (${{ matrix.os }})
    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-latest, windows-latest]
    runs-on: ${{ matrix.os }}
    defaults:
      run:
        shell: bash
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Test
        run: ./mvnw -B verify

      - name: Build application image
        run: packaging/build-app-image.sh

      - name: Smoke test (Linux)
        if: runner.os == 'Linux'
        run: target/dist/Sells/bin/Sells --smoke-test

      - name: Smoke test (Windows)
        if: runner.os == 'Windows'
        shell: pwsh
        run: |
          $p = Start-Process -FilePath "target\dist\Sells\Sells.exe" -ArgumentList "--smoke-test" -Wait -PassThru
          if ($p.ExitCode -ne 0) { throw "Smoke test failed with exit code $($p.ExitCode)" }

      - name: Archive (Linux)
        if: runner.os == 'Linux'
        run: |
          VERSION="$(./mvnw -q -B help:evaluate -Dexpression=project.version -DforceStdout)"
          tar -czf "target/dist/sells-${VERSION%-SNAPSHOT}-linux-x64.tar.gz" -C target/dist Sells

      - name: Archive (Windows)
        if: runner.os == 'Windows'
        shell: pwsh
        run: |
          $version = (./mvnw.cmd -q -B help:evaluate "-Dexpression=project.version" -DforceStdout).Trim() -replace '-SNAPSHOT$', ''
          Compress-Archive -Path target\dist\Sells -DestinationPath "target\dist\sells-$version-windows-x64.zip"

      - name: Report size
        run: du -sh target/dist/* || true

      - uses: actions/upload-artifact@v4
        with:
          name: sells-${{ runner.os }}
          path: |
            target/dist/*.zip
            target/dist/*.tar.gz
          if-no-files-found: error
```

### 13.6 Documentação a atualizar

1. `docs/adr/0012-empacotamento-jpackage.md`:
   - Status: `Aceita`. Ao final da linha, acrescente: `(instalador .msi adiado para depois do MVP)`.
   - Renomeie a seção "Proposta" para "Decisão" e substitua o item 3 por: "**Formato inicial:** pasta portátil (`jpackage --type app-image`), compactada em `.zip` no Windows e em `.tar.gz` no Linux. O instalador `.msi`, que exige o WiX Toolset, fica para depois do MVP."
   - Substitua o item 2 por: "**Runtime:** um JRE mínimo, gerado pelo `jlink` do próprio `jpackage` com a lista de módulos de `packaging/build-app-image.sh`."
   - Na seção "Critérios para aceitar a proposta", renomeie para "Validação" e acrescente: "O CI executa `Sells --smoke-test` no pacote gerado, em Linux e Windows. O smoke test inicia o PostgreSQL embutido num diretório temporário, aplica as migrations, cria o contexto do Spring e verifica os dados de localidade pt-BR. O tamanho de cada pacote é exibido no passo `Report size` do CI."
2. `docs/adr/0003-distribuicao-com-postgresql-embutido.md`: troque a linha "Validação no Windows: pendente, via GitHub Actions (`windows-latest`)." por "Validação no Windows: feita no CI (`windows-latest`), pelos testes de integração e pelo smoke test do pacote."

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`.
2. No Linux, `./mvnw -B package -DskipTests && packaging/build-app-image.sh && target/dist/Sells/bin/Sells --smoke-test` imprime `SMOKE TEST OK` e retorna o código 0.
3. `target/dist/Sells/bin/Sells`, sem argumentos (num ambiente com display), abre a interface.
4. `du -sh target/dist/Sells` é registrado no relatório.
5. O workflow é válido. Se houver `actionlint` disponível, rode-o; caso contrário, revise a indentação do YAML. A execução real no GitHub acontece quando a pessoa responsável fizer o push.

## Commit

```
build: package portable app image with jpackage and add CI for Linux and Windows
```
