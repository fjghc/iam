/**
 * Copyright (c) Istituto Nazionale di Fisica Nucleare (INFN). 2016-2018
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.infn.mw.iam.notification;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.velocity.VelocityEngineUtils;

import it.infn.mw.iam.api.account.password_reset.PasswordResetController;
import it.infn.mw.iam.core.IamDeliveryStatus;
import it.infn.mw.iam.core.IamNotificationType;
import it.infn.mw.iam.persistence.model.IamAccount;
import it.infn.mw.iam.persistence.model.IamEmailNotification;
import it.infn.mw.iam.persistence.model.IamGroupRequest;
import it.infn.mw.iam.persistence.model.IamNotificationReceiver;
import it.infn.mw.iam.persistence.model.IamRegistrationRequest;

public class TransientNotificationFactory implements NotificationFactory {

  private static final Logger LOG = LoggerFactory.getLogger(TransientNotificationFactory.class);
  private static final String RECIPIENT_FIELD = "recipient";
  private static final String ORGANISATION_NAME = "organisationName";
  private static final String USERNAME_FIELD = "username";
  private static final String GROUPNAME_FIELD = "groupName";

  @Value("${iam.baseUrl}")
  private String baseUrl;

  @Value("${iam.organisation.name}")
  private String organisationName;

  private final VelocityEngine velocityEngine;
  private final NotificationProperties properties;

  @Autowired
  public TransientNotificationFactory(VelocityEngine ve, NotificationProperties np) {
    this.velocityEngine = ve;
    this.properties = np;
  }

  @Override
  public IamEmailNotification createConfirmationMessage(IamRegistrationRequest request) {

    String recipient = request.getAccount().getUserInfo().getName();
    String confirmURL = String.format("%s/registration/verify/%s", baseUrl,
        request.getAccount().getConfirmationKey());

    Map<String, Object> model = new HashMap<>();
    model.put(RECIPIENT_FIELD, recipient);
    model.put("confirmURL", confirmURL);
    model.put(ORGANISATION_NAME, organisationName);

    IamEmailNotification notification = createMessage("confirmRegistration.vm", model,
        IamNotificationType.CONFIRMATION, properties.getSubject().get("confirmation"),
        request.getAccount().getUserInfo().getEmail());

    LOG.debug("Created confirmation message for registration request {}. Confirmation URL: {}",
        request.getUuid(), confirmURL);

    return notification;
  }

  @Override
  public IamEmailNotification createAccountActivatedMessage(IamRegistrationRequest request) {

    String recipient = request.getAccount().getUserInfo().getName();
    String resetPasswordUrl = String.format("%s%s/%s", baseUrl,
        PasswordResetController.BASE_TOKEN_URL, request.getAccount().getResetKey());

    Map<String, Object> model = new HashMap<>();
    model.put(RECIPIENT_FIELD, recipient);
    model.put("resetPasswordUrl", resetPasswordUrl);
    model.put(ORGANISATION_NAME, organisationName);

    IamEmailNotification notification = createMessage("accountActivated.vm", model,
        IamNotificationType.ACTIVATED, properties.getSubject().get("activated"),
        request.getAccount().getUserInfo().getEmail());

    LOG.debug(
        "Create account activated message for registration request {}. Reset password URL: {}",
        request.getUuid(), resetPasswordUrl);

    return notification;
  }

  @Override
  public IamEmailNotification createRequestRejectedMessage(IamRegistrationRequest request) {
    String recipient = request.getAccount().getUserInfo().getName();

    Map<String, Object> model = new HashMap<>();
    model.put(RECIPIENT_FIELD, recipient);
    model.put(ORGANISATION_NAME, organisationName);

    return createMessage("requestRejected.vm", model, IamNotificationType.REJECTED,
        properties.getSubject().get("rejected"), request.getAccount().getUserInfo().getEmail());
  }

  @Override
  public IamEmailNotification createAdminHandleRequestMessage(IamRegistrationRequest request) {
    String name = request.getAccount().getUserInfo().getName();
    String username = request.getAccount().getUsername();
    String email = request.getAccount().getUserInfo().getEmail();

    Map<String, Object> model = new HashMap<>();
    model.put("name", name);
    model.put(USERNAME_FIELD, username);
    model.put("email", email);
    model.put("indigoDashboardUrl", String.format("%s/dashboard#/requests", baseUrl));
    model.put(ORGANISATION_NAME, organisationName);
    model.put("notes", request.getNotes());

    return createMessage("adminHandleRequest.vm", model, IamNotificationType.CONFIRMATION,
        properties.getSubject().get("adminHandleRequest"), properties.getAdminAddress());
  }

  @Override
  public IamEmailNotification createResetPasswordMessage(IamAccount account) {

    String recipient = account.getUserInfo().getName();
    String resetPasswordUrl = String.format("%s%s/%s", baseUrl,
        PasswordResetController.BASE_TOKEN_URL, account.getResetKey());

    Map<String, Object> model = new HashMap<>();
    model.put(RECIPIENT_FIELD, recipient);
    model.put("resetPasswordUrl", resetPasswordUrl);
    model.put(ORGANISATION_NAME, organisationName);
    model.put(USERNAME_FIELD, account.getUsername());

    IamEmailNotification notification =
        createMessage("resetPassword.vm", model, IamNotificationType.RESETPASSWD,
            properties.getSubject().get("resetPassword"), account.getUserInfo().getEmail());

    LOG.debug("Created reset password message for account {}. Reset password URL: {}",
        account.getUsername(), resetPasswordUrl);

    return notification;
  }

  @Override
  public IamEmailNotification createAdminHandleGroupRequestMessage(IamGroupRequest groupRequest) {
    String groupName = groupRequest.getGroup().getName();

    Map<String, Object> model = new HashMap<>();
    model.put("name", groupRequest.getAccount().getUserInfo().getName());
    model.put(USERNAME_FIELD, groupRequest.getAccount().getUsername());
    model.put(GROUPNAME_FIELD, groupName);
    model.put("notes", groupRequest.getNotes());
    model.put("indigoDashboardUrl", String.format("%s/dashboard#/requests", baseUrl));
    model.put(ORGANISATION_NAME, organisationName);

    String subject = String.format("New membership request for group %s", groupName);

    LOG.debug("Create group membership admin notification for request {}", groupRequest.getUuid());
    return createMessage("adminHandleGroupRequest.vm", model, IamNotificationType.GROUP_MEMBERSHIP,
        subject, properties.getAdminAddress());
  }

  @Override
  public IamEmailNotification createGroupMembershipApprovedMessage(IamGroupRequest groupRequest) {
    String recipient = groupRequest.getAccount().getUserInfo().getName();
    String groupName = groupRequest.getGroup().getName();
    String status = groupRequest.getStatus().name();

    Map<String, Object> model = new HashMap<>();
    model.put(RECIPIENT_FIELD, recipient);
    model.put(GROUPNAME_FIELD, groupName);
    model.put("status", status);
    model.put(ORGANISATION_NAME, organisationName);

    String subject =
        String.format("Membership request for group %s has been %s", groupName, status);

    IamEmailNotification notification =
        createMessage("groupMembershipApproved.vm", model, IamNotificationType.GROUP_MEMBERSHIP,
            subject, groupRequest.getAccount().getUserInfo().getEmail());

    LOG.debug("Create group membership approved message for request {}", groupRequest.getUuid());
    return notification;
  }

  @Override
  public IamEmailNotification createGroupMembershipRejectedMessage(IamGroupRequest groupRequest) {
    String recipient = groupRequest.getAccount().getUserInfo().getName();
    String groupName = groupRequest.getGroup().getName();
    String status = groupRequest.getStatus().name();

    Map<String, Object> model = new HashMap<>();
    model.put(RECIPIENT_FIELD, recipient);
    model.put(GROUPNAME_FIELD, groupName);
    model.put("status", status);
    model.put("motivation", groupRequest.getMotivation());
    model.put(ORGANISATION_NAME, organisationName);

    String subject =
        String.format("Membership request for group %s has been %s", groupName, status);

    IamEmailNotification notification =
        createMessage("groupMembershipRejected.vm", model, IamNotificationType.GROUP_MEMBERSHIP,
            subject, groupRequest.getAccount().getUserInfo().getEmail());

    LOG.debug("Create group membership approved message for request {}", groupRequest.getUuid());
    return notification;
  }


  protected IamEmailNotification createMessage(String template, Map<String, Object> model,
      IamNotificationType messageType, String subject, String receiverAddress) {

    String body =
        VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, template, "UTF-8", model);

    IamEmailNotification message = new IamEmailNotification();
    message.setUuid(UUID.randomUUID().toString());
    message.setType(messageType);
    message.setSubject(subject);
    message.setBody(body);
    message.setCreationTime(new Date());
    message.setDeliveryStatus(IamDeliveryStatus.PENDING);

    List<IamNotificationReceiver> receivers = new ArrayList<>();
    IamNotificationReceiver rcv = new IamNotificationReceiver();
    rcv.setIamEmailNotification(message);
    rcv.setEmailAddress(receiverAddress);
    receivers.add(rcv);

    message.setReceivers(receivers);
    return message;
  }
}
