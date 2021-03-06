// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JGitConfigTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private SitePaths site;
  private Path gitPath;

  @Before
  public void setUp() throws IOException {
    site = new SitePaths(temporaryFolder.newFolder().toPath());
    Files.createDirectories(site.etc_dir);
    gitPath = Files.createDirectories(site.resolve("git"));

    Files.write(
        site.jgit_config, "[core]\n  trustFolderStat = false\n".getBytes(StandardCharsets.UTF_8));
    new SystemReaderInstaller(site).start();
  }

  @Test
  public void test() throws IOException {
    try (Repository repo = new FileRepository(gitPath.resolve("foo").toFile())) {
      assertThat(repo.getConfig().getString("core", null, "trustFolderStat")).isEqualTo("false");
    }
  }

  @Test
  public void openSystemConfigRespectsParent() throws Exception {
    Config parent = new Config();
    parent.setString("foo", null, "bar", "value");
    FileBasedConfig system = SystemReader.getInstance().openSystemConfig(parent, FS.DETECTED);
    system.load();
    assertThat(system.getString("core", null, "trustFolderStat")).isEqualTo("false");
    assertThat(system.getString("foo", null, "bar")).isEqualTo("value");
  }

  @Test
  public void openSystemConfigReturnsDifferentInstances() throws Exception {
    FileBasedConfig system1 = SystemReader.getInstance().openSystemConfig(null, FS.DETECTED);
    system1.load();
    assertThat(system1.getString("core", null, "trustFolderStat")).isEqualTo("false");

    FileBasedConfig system2 = SystemReader.getInstance().openSystemConfig(null, FS.DETECTED);
    system2.load();
    assertThat(system2.getString("core", null, "trustFolderStat")).isEqualTo("false");

    system1.setString("core", null, "trustFolderStat", "true");
    assertThat(system1.getString("core", null, "trustFolderStat")).isEqualTo("true");
    assertThat(system2.getString("core", null, "trustFolderStat")).isEqualTo("false");
  }
}
