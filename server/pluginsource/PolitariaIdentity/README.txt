POLITARIA IDENTITY SOURCE BOOTSTRAP

1. Open PowerShell in this folder.
2. Run:

   powershell -ExecutionPolicy Bypass -File .\setup-source.ps1

   This downloads CFR and decompiles the ORIGINAL working PolitariaIdentity.jar
   into src\main\java\ru\politaria\identity\

3. Then compile:

   mvn clean package

4. Result:

   target\PolitariaIdentity.jar

IMPORTANT:
- This archive contains the original working PolitariaIdentity.jar, not the broken tags-patched jar.
- Java target: 21
- Server API target: Paper/Purpur 1.21.1
- If CFR-generated Java has compile errors, send the complete `mvn clean package` output.
