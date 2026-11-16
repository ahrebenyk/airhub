; Inno Setup script of the AirHub installer.
; Built by installer\build.ps1, which first creates the program image with jpackage
; (AirHub.exe launcher + application jar + Java runtime) in target\installer\image\AirHub.
; Manual build: ISCC.exe /DAppVersion=1.1.0 installer\airhub.iss

#ifndef AppVersion
  #define AppVersion "1.1.0"
#endif
#ifndef ImageDir
  #define ImageDir "..\target\installer\image\AirHub"
#endif
#ifndef DocsDir
  #define DocsDir "..\target\installer\docs"
#endif
#define AppName "AirHub"
#define AppExe "AirHub.exe"
#define AppPublisher "Гребеник Артем"
#define SupportEmail "artem.grebenyk@gmail.com"
; directory of a database shared by all users of the computer (task "shareddb")
#define SharedDataDir "{commonappdata}\AirHub"

[Setup]
; AppId identifies the program for updates and uninstallation: it must not change between versions
AppId={{5B7C2E8A-4D3F-4E8B-9A61-3C2D7E0F1A42}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppContact={#SupportEmail}
AppSupportURL=mailto:{#SupportEmail}
AppComments=Система управління аеропортом
VersionInfoVersion={#Copy(AppVersion, 1, Pos("-", AppVersion + "-") - 1)}
VersionInfoDescription={#AppName} — інсталятор
; the user chooses: for all users (Program Files, administrator rights) or only for me (no administrator rights)
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
; the bundled Java runtime is 64-bit
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
SetupIconFile=airhub.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName} {#AppVersion}
OutputDir=..\target\installer
OutputBaseFilename={#AppName}-{#AppVersion}-setup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
; a running AirHub keeps its files locked: the Restart Manager offers to close it
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "ukrainian"; MessagesFile: "compiler:Languages\Ukrainian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "shareddb"; Description: "Спільна база даних для всіх користувачів комп'ютера ({#SharedDataDir})"; \
    GroupDescription: "База даних:"; Flags: unchecked; Check: IsAdminInstallMode

[Files]
; program image built by jpackage: launcher, app\ (jar with H2 and FlatLaf, AirHub.cfg), runtime\ (Java runtime)
Source: "{#ImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#DocsDir}\*"; DestDir: "{app}\docs"; Flags: ignoreversion recursesubdirs skipifsourcedoesntexist

[Dirs]
; ordinary users must be able to create and change the shared database files
Name: "{#SharedDataDir}"; Permissions: users-modify; Tasks: shareddb; Flags: uninsneveruninstall

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"; WorkingDir: "{app}"
Name: "{group}\Інструкція користувача"; Filename: "{app}\docs\AirHub - Експлуатаційна документація.pdf"; \
    Check: FileExists(ExpandConstant('{app}\docs\AirHub - Експлуатаційна документація.pdf'))
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent

[Code]
const
  LauncherConfig = '{app}\app\AirHub.cfg';

{ The jpackage launcher reads JVM options from app\AirHub.cfg; the shared database directory is passed
  to the program as -Dairhub.data (see Db.dataDir) }
procedure UseSharedDatabase;
var
  Lines: TArrayOfString;
  NewLines: TArrayOfString;
  I, J: Integer;
  FileName, DataDir: String;
begin
  FileName := ExpandConstant(LauncherConfig);
  if not LoadStringsFromFile(FileName, Lines) then begin
    MsgBox('Не вдалося прочитати ' + FileName, mbError, MB_OK);
    Exit;
  end;
  SetArrayLength(NewLines, GetArrayLength(Lines) + 1);
  { forward slashes: the launcher configuration is not a Windows-specific format }
  DataDir := ExpandConstant('{#SharedDataDir}');
  StringChangeEx(DataDir, '\', '/', True);
  J := 0;
  for I := 0 to GetArrayLength(Lines) - 1 do begin
    NewLines[J] := Lines[I];
    J := J + 1;
    if Lines[I] = '[JavaOptions]' then begin
      NewLines[J] := 'java-options=-Dairhub.data=' + DataDir;
      J := J + 1;
    end;
  end;
  SaveStringsToUTF8FileWithoutBOM(FileName, NewLines, False);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if (CurStep = ssPostInstall) and WizardIsTaskSelected('shareddb') then
    UseSharedDatabase;
end;

{ The database is user data: it is deleted only if the user agrees (silent uninstall keeps it) }
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  DataDir: String;
begin
  if CurUninstallStep <> usPostUninstall then
    Exit;
  DataDir := ExpandConstant('{localappdata}\AirHub');
  if DirExists(DataDir) and
     (SuppressibleMsgBox('Видалити базу даних AirHub (рейси, користувачі, журнал)?' + #13#10 + DataDir,
                         mbConfirmation, MB_YESNO or MB_DEFBUTTON2, IDNO) = IDYES) then
    DelTree(DataDir, True, True, True);
end;
