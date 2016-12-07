package com.mirantis.mcp

/**
 * Parse HEAD of current directory and return commit hash
 */
def getGitCommit() {
    git_commit = sh(
            script: 'git rev-parse HEAD',
            returnStdout: true
    ).trim()
    return git_commit
}

/**
 * Describe a commit using the most recent tag reachable from it
 *
 * @param useShort Boolean, which String format returns as result.
 *              false (Default): {gitTag}-{numCommits}-g{gitsha}
 *              true:            {gitTag}-{numCommits}
 */
def getGitDescribe(Boolean useShort = false) {
    if (useShort) {
        // original sed "s/-g[0-9a-f]\+$//g" should be escaped in groovy
        git_commit = sh (
                script: 'git describe --tags | sed "s/-g[0-9a-f]\\+$//g"',
                returnStdout: true
        ).trim()
    } else {
        git_commit = sh (
                script: 'git describe --tags',
                returnStdout: true
        ).trim()
    }
    return git_commit
}

/**
 * Execute git clone+checkout stage for some project,
 * through SSH
 *
 * @param body Closure
 *        body includes next parameters:
 *          - credentialsId, id of user which should make checkout
 *          - branch, branch of project
 *          - host, gerrit-ci hostname
 *          - project, name of project
 *          - targetDir, target directory of cloned repo
 *          - withMerge, prevent detached mode in repo
 *
 * Usage example:
 *
 * def gitFunc = new com.mirantis.mcp.Git()
 * gitFunc.gitSSHCheckout {
 *   credentialsId = 'mcp-ci-gerrit'
 *   branch = 'mcp-0.1'
 *   host = 'ci.mcp-ci.local'
 *   project = 'project'
 * }
 */
def gitSSHCheckout = { body ->
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def merge = config.withMerge ?: false
  def targetDir = config.targetDir ?: "./"
  def port = config.port ?: "29418"

  // default parameters
  def scmExtensions = [
    [$class: 'CleanCheckout'],
    [$class: 'RelativeTargetDirectory', relativeTargetDir: "${targetDir}"]
  ]

  // https://issues.jenkins-ci.org/browse/JENKINS-6856
  if (merge) {
    scmExtensions.add([$class: 'LocalBranch', localBranch: "${config.branch}"])
  }

  checkout(
    scm: [
      $class: 'GitSCM',
      branches: [[name: "${config.branch}"]],
      extensions: scmExtensions,
      userRemoteConfigs: [[
        credentialsId: "${config.credentialsId}",
        name: 'origin',
        url: "ssh://${config.credentialsId}@${config.host}:${port}/${config.project}.git"
      ]]
    ]
  )
}

/**
 * Execute git clone and checkout stage from gerrit review
 *
 * @param body Closure
 *        body includes next parameters:
 *          - credentialsId, id of user which should make checkout
 *          - withMerge, prevent detached mode in repo
 *          - withWipeOut, wipe repository and force clone
 *
 * Usage example:
 *
 * def gitFunc = new com.mirantis.mcp.Git()
 * gitFunc.gerritPatchsetCheckout {
 *   credentialsId = 'mcp-ci-gerrit'
 *   withMerge = true
 * }
 */
def gerritPatchsetCheckout = { body ->
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()


  def merge = config.withMerge ?: false
  def wipe = config.withWipeOut ?: false

  // default parameters
  def scmExtensions = [
    [$class: 'CleanCheckout'],
    [$class: 'BuildChooserSetting', buildChooser: [$class: 'GerritTriggerBuildChooser']]
  ]
  // if we need to "merge" code from patchset to GERRIT_BRANCH branch
  if (merge) {
    scmExtensions.add([$class: 'LocalBranch', localBranch: "${GERRIT_BRANCH}"])
  }
  // we need wipe workspace before checkout
  if (wipe) {
    scmExtensions.add([$class: 'WipeWorkspace'])
  }

  checkout(
    scm: [
      $class: 'GitSCM',
      branches: [[name: "${GERRIT_BRANCH}"]],
      extensions: scmExtensions,
      userRemoteConfigs: [[
        credentialsId: "${config.credentialsId}",
        name: 'gerrit',
        url: "ssh://${GERRIT_NAME}@${GERRIT_HOST}:${GERRIT_PORT}/${GERRIT_PROJECT}.git",
        refspec: "${GERRIT_REFSPEC}"
      ]]
    ]
  )
}
