# CaMoF (Campus Mobility Framework)

This repository is forked from Mobility Framework CaMoF(https://github.com/H-Fehler/CaMoF) and expands on the Single Destination Commute Trip Sharing Problem (SDCTSP). 
Solving the problem with a clustering heuristic(https://www1.pub.informatik.uni-wuerzburg.de/pub/theses/2024-barth-praktikumsbericht.pdf) 
and a Mixed-Integer Linear Program inspired by https://pubsonline.informs.org/doi/abs/10.1287/trsc.2019.0969.

## Problem Description

The SDCTSP is a Ridesharing problem, where multiple users with unique start locations want to arrive at one destination(shared for all users) and then return to their starting locations at the end of the day.  
The users have individual time windows and can own their own vehicles, that they can share with other users, so the overall distance travelled is minimized.  
Different from similar problem definitions, in this problem variation users can be grouped differently for in- and outbounding routes.


## Installation

- Clone the repository from GitHub
- Download target.zip from https://www.dropbox.com/scl/fo/ir9avrvfxtd1wd0k4y3z9/AH6ECB3kcTlFsdisMPkkkgE?rlkey=hx1nrl8ghzx5r00tisvg9930z&e=1&st=r7xgquje&dl=0 and extract into target folder
- install java 17 or higher
- install CPLEX Developer Edition 22.11 or higher
- add path to cplex at the start of build.gradle.kts
- build the project with **gradlew.bat build**

## Running Code

- Create Configuration file, (see sources/configMine.json), important parameters:
  - set request file (see sources/agentDistribution/requestData)
  - adjust percentWilling to a value between 1 - 100 to only keep percentage of all 6500 requests
  - set mode under "modes" to set solve strategy
    - EverybodyDrives (everyone drives in own vehicle)
    - ExactSolution (solves instance exact)
    - SwitchDriver (uses Clustering Heuristic)
- Run File "GeneralManager.java" with parameters "startModes" and path to configuration file