package com.fintechfive.ftrade.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Client {
    private Long clientId;
    private UUID externalAccountId;
    private String firstName;
    private String lastName;
    private String email;
    private LocalDate birthDate;
    private List<Account> clientAccounts;

    public Client() {
        this.clientAccounts = new ArrayList<>();
    }

    public Client(String firstName, String lastName, LocalDate birthDate) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.clientAccounts = new ArrayList<>();
    }

    public Client(String firstName, String lastName, String email, LocalDate birthDate) {
        this(firstName, lastName, birthDate);
        this.email = email;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public UUID getExternalAccountId() {
        return externalAccountId;
    }

    public void setExternalAccountId(UUID externalAccountId) {
        this.externalAccountId = externalAccountId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public List<Account> getClientAccounts() {
        return clientAccounts;
    }

    public void setClientAccounts(List<Account> clientAccounts) {
        this.clientAccounts = clientAccounts;
    }
}
