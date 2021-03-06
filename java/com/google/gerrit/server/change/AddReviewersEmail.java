// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.mail.Address;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.send.AddReviewerSender;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Singleton
public class AddReviewersEmail {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final ExecutorService sendEmailsExecutor;

  @Inject
  AddReviewersEmail(
      AddReviewerSender.Factory addReviewerSenderFactory,
      @SendEmailExecutor ExecutorService sendEmailsExecutor) {
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.sendEmailsExecutor = sendEmailsExecutor;
  }

  public void emailReviewersAsync(
      IdentifiedUser user,
      Change change,
      Collection<Account.Id> added,
      Collection<Account.Id> copied,
      Collection<Address> addedByEmail,
      Collection<Address> copiedByEmail,
      NotifyResolver.Result notify) {
    // The user knows they added themselves, don't bother emailing them.
    Account.Id userId = user.getAccountId();
    ImmutableList<Account.Id> immutableToMail =
        added.stream().filter(id -> !id.equals(userId)).collect(toImmutableList());
    ImmutableList<Account.Id> immutableToCopy =
        copied.stream().filter(id -> !id.equals(userId)).collect(toImmutableList());
    if (immutableToMail.isEmpty()
        && immutableToCopy.isEmpty()
        && addedByEmail.isEmpty()
        && copiedByEmail.isEmpty()) {
      return;
    }

    // Make immutable copies of collections and hand over only immutable data types to the other
    // thread.
    Change.Id cId = change.getId();
    Project.NameKey projectNameKey = change.getProject();
    ImmutableList<Address> immutableAddedByEmail = ImmutableList.copyOf(addedByEmail);
    ImmutableList<Address> immutableCopiedByEmail = ImmutableList.copyOf(copiedByEmail);

    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        sendEmailsExecutor.submit(
            () -> {
              try {
                AddReviewerSender cm = addReviewerSenderFactory.create(projectNameKey, cId);
                cm.setNotify(notify);
                cm.setFrom(userId);
                cm.addReviewers(immutableToMail);
                cm.addReviewersByEmail(immutableAddedByEmail);
                cm.addExtraCC(immutableToCopy);
                cm.addExtraCCByEmail(immutableCopiedByEmail);
                cm.send();
              } catch (Exception err) {
                logger.atSevere().withCause(err).log(
                    "Cannot send email to new reviewers of change %s", change.getId());
              }
            });
  }
}
