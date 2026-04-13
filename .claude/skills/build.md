# 빌드 검증

코드 파일(*.kt, *.kts)이 변경된 경우 빌드를 실행한다:

```bash
powershell.exe -Command '& { $env:JAVA_HOME="C:\Users\tpgj9\.jdks\openjdk-26"; Set-Location "C:\Users\tpgj9\IdeaProjects\fds"; .\gradlew.bat classes --no-daemon 2>&1 }'
```

빌드 실패 시 작업을 중단하고 오류를 보고한다.

## 특정 모듈 테스트
```bash
powershell.exe -Command '& { $env:JAVA_HOME="C:\Users\tpgj9\.jdks\openjdk-26"; Set-Location "C:\Users\tpgj9\IdeaProjects\fds"; .\gradlew.bat {모듈}:test --no-daemon 2>&1 }'
```
