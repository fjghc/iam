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
package it.infn.mw.iam.api.scim.model;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.infn.mw.iam.api.scim.model.ScimUser.NewUserValidation;
import it.infn.mw.iam.api.scim.model.ScimUser.UpdateUserValidation;

public class ScimEmail {

  public enum ScimEmailType {
    work, home, other;
  }

  @NotNull
  @Valid
  private final ScimEmailType type;

  @NotEmpty(groups = {NewUserValidation.class, UpdateUserValidation.class})
  @Email(groups = {NewUserValidation.class, UpdateUserValidation.class})
  private final String value;

  @NotNull(groups = {NewUserValidation.class, UpdateUserValidation.class})
  private final Boolean primary;

  @JsonCreator
  private ScimEmail(@JsonProperty("type") ScimEmailType type, @JsonProperty("value") String value,
      @JsonProperty("primary") Boolean primary) {
    this.type = type;
    this.value = value;
    this.primary = primary;
  }

  private ScimEmail(Builder b) {
    this.type = b.type;
    this.value = b.value;
    this.primary = b.primary;
  }

  public ScimEmailType getType() {

    return type;
  }

  public String getValue() {

    return value;
  }

  public Boolean getPrimary() {

    return primary;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ScimEmail other = (ScimEmail) obj;
    if (value == null) {
      if (other.value != null)
        return false;
    } else if (!value.equals(other.value))
      return false;
    return true;
  }

  public static Builder builder() {

    return new Builder();
  }

  public static class Builder {

    private ScimEmailType type;
    private String value;
    private Boolean primary;

    public Builder() {
      type = ScimEmailType.work;
      primary = true;
    }

    public Builder email(String value) {

      this.value = value;
      return this;
    }

    public ScimEmail build() {

      return new ScimEmail(this);
    }

  }

}
