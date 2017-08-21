#!/bin/bash
# Script to keep the HCALFM builds organized
# Production HCALFMs must be built from an unmodified git commit, and are indexed by their commit hash.
# Test HCALFMs are organized by date, appended with a version number.
# Release convention: yy.xx.zz   where yy = year, xx= major version, zz=minor version
# Usage 1) test build
#       ./buildHCALFM.sh test
# Usage 2) Build major release
#       ./buildHCALFM.sh release major
# Usage 3) Build minor release: 
#       ./buildHCALFM.sh release minor
# OR  
#       ./buildHCALFM.sh release
# Usage 4) Build HCALFM.jar with commit hash
#       ./buildHCALFM.sh hash
#
# Created: John Hakala 4/14/2016
# Modified: Martin Kwok 8/14/2017

if [ "$1" = "release" ]; then
  git diff-index --quiet HEAD
  if [ "$?" = "0" ]; then
    #get the latest tag version
    release=`git tag -l | sort --field-separator=. -k3 -n | sort --field-separator=. -k2 -n | tail -1`
    Year=`date  +%y`
    versionArr=(${release//./ })
    remotes=`git remote -v | grep HCALRunControl | tail -1`
    RC_remote=(${remotes[0]})
    git fetch $RC_remote --tags
    if [ "$2" = "major" ]; then
      GITREV="${Year}.$((versionArr[1]+1)).0"
      GITREV_fname="${Year}_$((versionArr[1]+1))_0"
    else
      #Default is minor increment
      GITREV="${Year}.${versionArr[1]}.$((versionArr[2]+1))"
      GITREV_fname="${Year}_${versionArr[1]}_$((versionArr[2]+1))"
    fi
    echo "Building HCALFM release: $GITREV"
    sed -i '$ d' ../gui/jsp/footer.jspf
    echo '<div id="hcalfmVersion"><a href="https://github.com/HCALRunControl/levelOneHCALFM/commit/'"${tagCommit}\">HCALFM version:${GITREV} </a></div>" >> ../gui/jsp/footer.jspf
    ant -DgitRev="${GITREV_fname}"
    echo "Tagging HCALFM release: $GITREV"
    git tag $GITREV 
    tagCommit=`git rev-list -n 1 $GITREV  | head -c 7`
    git push $RC_remote $GITREV 
  else
    echo "No changes since the last commit are permitted when building a release FM. Please commit your changes or stash them."
    exit 1
  fi
elif [ "$1" = "hash" ]; then
  git diff-index --quiet HEAD
  if [ "$?" = "0" ]; then
    commitHash=`git rev-parse HEAD | head -c 7`
    sed -i '$ d' ../gui/jsp/footer.jspf
    echo '<div id="hcalfmVersion"><a href="https://github.com/HCALRunControl/levelOneHCALFM/commit/'"${commitHash}\">HCALFM version:${commitHash} </a></div>" >> ../gui/jsp/footer.jspf
    ant -DgitRev="${commitHash}"
  else
    echo "No changes since the last commit are permitted when building FM with hash stamp. Please commit your changes or stash them."
    exit 1
  fi
elif [ "$1" = "test" ]; then
  DATE=`date  +%m-%d-%y`
  ITERATION=1
  
  while [ -f jars/HCALFM_${DATE}_v${ITERATION}.jar ];
    do ITERATION=$(($ITERATION + 1))
  done
  sed -i '$ d' ../gui/jsp/footer.jspf
  echo "<div id='hcalfmVersion'>HCALFM version: ${DATE}_v${ITERATION}</div>" >> ../gui/jsp/footer.jspf
  ant -DgitRev="${DATE}_v${ITERATION}"

else 
  echo "Please run buildHCALFM with either the 'release' or 'test' option. Example:"
  echo "./buildHCALFM.sh test"
  exit 1

fi
