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
    releaseTags=`git tag -l | sort --field-separator=. -k3 -n | sort --field-separator=. -k2 -n`
    Year=`date  +%y`
    versionArr=(${release//./ })
    remotes=`git remote -v | grep HCALRunControl | tail -1`
    RC_remote=(${remotes[0]})
    echo "Fetching all HCALFM releases from github ..."
    git fetch $RC_remote --tags
    if [ "$2" = "major" ]; then
      GITREV="${Year}.$((versionArr[1]+1)).0"
      GITREV_fname="${Year}_$((versionArr[1]+1))_0"
    elif [ "$2" = "minor" ]; then
      GITREV="${Year}.${versionArr[1]}.$((versionArr[2]+1))"
      GITREV_fname="${Year}_${versionArr[1]}_$((versionArr[2]+1))"
    else
      #Check if input is a valid release tags
      inputTag=$2
      isValidRelease="false"
      for tag in ${releaseTags}
      do
          if [ "$tag" == "${inputTag}" ]; then
            isValidRelease="true"
          fi
      done
      if [ $isValidRelease == "true" ] ; then
        GITREV="${inputTag}"
        fnameArr=(${GITREV//./_})
        GITREV_fname="${fnameArr[*]}"
        currentBranch=`git branch | grep \* | cut -d ' ' -f2`
        echo Chcking out to a new branch for building previous release
        git checkout $GITREV -b "build_"$GITREV
      else
         printf "$inputTag is not a valid release tag!\n Usage: ./buildHCALFM release [major|minor|releaseTag] \n Use \"git tag -l\" to see all the releases. \n"
         exit -1
      fi
    fi
    echo "Building HCALFM release: $GITREV"
    tagCommit=`git rev-parse HEAD | head -c 7`
    sed -i '$ d' ../gui/jsp/footer.jspf
    echo '<div id="hcalfmVersion"><a href="https://github.com/HCALRunControl/levelOneHCALFM/commit/'"${tagCommit}\">HCALFM version:${GITREV} </a></div>" >> ../gui/jsp/footer.jspf
    ant -DgitRev="${GITREV_fname}"
    #Update the tags only if build successful and not building old release
    if [ "$?" = "0" ]; then
      if [ $isValidRelease == "false" ]; then
        echo "Tagging HCALFM release: $GITREV"
        git tag $GITREV 
        echo "Pushing tag to gihub ... "
        git push $RC_remote $GITREV 
      else
        echo "Returning to this branch: " $currentBranch
        git checkout $currentBranch
      fi
    else
      echo "Build not successful, tags are not updated"
    fi
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
