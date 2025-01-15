package org.monxef.gbank.enums;

public enum TransactionType {
    DEPOSIT,         // Money added to account
    WITHDRAWAL,      // Money removed from account
    TRANSFER_IN,     // Money received from another player
    TRANSFER_OUT,    // Money sent to another player
    INTEREST,        // Interest earned on balance
    AUTOMATIC,       // Automatic payment or system operation
    ADMIN_SET,       // Admin set balance command
    ADMIN_RESET,     // Admin reset account command
    ADMIN_TRANSFER,  // Admin transfer between accounts
    SYSTEM          // System-related transaction
}
