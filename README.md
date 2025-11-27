# Sleeping Server Stopper
[![Modrinth](https://img.shields.io/badge/Modrinth-EmptyServerStopper-green?logo=modrinth&logoColor=white)](https://modrinth.com/project/nuRsj5Nz) Get on Modrinth!

This mod is a port of the [Sleeping Server Stopper Spigot plugin](https://github.com/vincss/mcEmptyServerStopper). Supported versions are:

| MC Version ↓ / Plugin Version → | **3.0.0** | **2.0.0** | **1.0.0** |
| :-----------------------------: | :-------: | :-------: | :-------: |
| 1.7.10                         |    ⚙️     |    ❌     |    ❌     |
| 1.12.2                         |    ⚙️     |    ❌     |    ❌     |
| 1.16.5                         |    ✅     |    ❌     |    ❌     |
| 1.18.2                         |    ✅     |    ❌     |    ❌     |
| 1.19.X                         |    ✅     |    ✅     |    ❌     |
| 1.20.1                         |    ✅     |    ✅     |    ✅     |

✅ - Stable Release
🟨 - In Development
⚙️ - BETA Release

This is meant to be used with the mod [SleepingServerStarter](https://github.com/vincss/mcsleepingserverstarter) by [vincss](https://github.com/vincss).

## 🤝 Contributers
Author: [MadaHaz](https://github.com/MadaHaz)

- [OneWeekNotice](https://github.com/OneWeekNotice) - Tested the 1.16.5 version.

I'm basing my mod off of another port which has been archived, [EmptyServerStopper](https://github.com/GHYAKIMA/emptyserverstopper-mod) by [GHYAKIMA](https://github.com/GHYAKIMA). I couldn't properly fork their project, they had some dual development setup for both Fabric and Forge, couldn't make any sense of it, so I decided to remake it myself and add some features.

## 🛠 TODO
- ~~Add config file.~~ **DONE**
- ~~Clean up files in repo (Organise folders into versions?)~~ **DONE**
- ~~Update 1.19 to v3.0.0~~ **DONE**
- ~~Finish 1.12.2 port.~~ **DONE**
- Redo the versions so that a single mod file supports all minor versions of a major version.
  - E.g. Get rid of 1.18.2 and replace it with 1.18 which supports all 1.18.X versions. Hopefully the forge devs didn't make too many drastic changes for each minor version.

## ⚠ NOTE ⚠
An issue came up where versions before 1.20.1 aren't working, most likely because the mod was coded using Forge 47.2.0. I will attempt to remake the mod for any other version that is requested (Just open up an issue) since the mod really isn't that hard to make.

## 📄 Releases
- **v3.0.0-1.18.2**
  - Forge 1.18.2
  - Coded in Forge 1.18.2 36.2.34
- **v3.0.0-1.7.10**
  - Forge 1.7.10
  - Coded in Forge 1.7.10 10.13.4.1614-1.7.10
  - Template from: https://github.com/anatawa12/ForgeGradle-1.2
- **v3.0.0-1.16.5**
  - Forge 1.16.5
  - Coded in Forge 1.16.5 36.2.34
- **v3.0.0-1.20.1**
  - Forge 1.20.1
  - Coded in Forge 1.20.1 47.2.0.
- **v3.0.0-1.19**
  - Forge 1.19
  - Coded in Forge 1.19 41.1.0
- **v2.0.0-1.19**
  - Forge 1.19
  - Coded in Forge 1.19 41.1.0
- **v2.0.0-1.20.1**
  - Forge 1.20.1
  - Coded in Forge 1.20.1 47.2.0.
- **v1.0.0-1.20.1**
  - Forge 1.20.1
  - Coded in Forge 1.20.1 47.2.0.
 
## 📄 Versions
- **v3.0.0**
  - Features:
    - Config File
  - Changes:
    - Config files "shutdownOnLaunch" value now works.
- **v2.0.0**
  - Features:
    - Config File
  - Changes:
    - Config File is generated. Values that can be changed are:
      - SHUTDOWN_TIME_IN_MINUTES - How many minutes until the server closes. (Minimum is 1 minute!)
      - SHUTDOWN_SERVER_ON_LAUNCH - Should the server shutdown on launch?
    - Default config values are (2, true).
    - Fixed 2 minor bugs.
- **v1.0.0**
  - Hard coded values. 2 minute timer and the server will close on startup.
