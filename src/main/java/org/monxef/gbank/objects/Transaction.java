package org.monxef.gbank.objects;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.monxef.gbank.enums.TransactionType;

@Getter
@RequiredArgsConstructor
public class Transaction {
    private final TransactionType type;
    private final double amount;
    private final long timestamp;
    private final String details;

    /**
     * Creates a new transaction with the current timestamp
     *
     * @param type The type of transaction
     * @param amount The amount involved in the transaction
     * @param details Additional details about the transaction
     */
    public Transaction(TransactionType type, double amount, String details) {
        this(type, amount, System.currentTimeMillis(), details);
    }

    /**
     * Formats the transaction amount with the given currency prefix
     *
     * @param currencyPrefix The prefix to use (e.g., "$" or "€")
     * @return Formatted amount with currency prefix
     */
    public String getFormattedAmount(String currencyPrefix) {
        String sign = amount >= 0 ? "+" : "";
        return String.format("%s%s%.2f", currencyPrefix, sign, amount);
    }

    /**
     * Creates a human-readable string representation of the transaction
     *
     * @param currencyPrefix The currency prefix to use in formatting
     * @return A formatted string describing the transaction
     */
    public String format(String currencyPrefix) {
        return String.format("[%s] %s - %s",
                type.toString(),
                getFormattedAmount(currencyPrefix),
                details);
    }

    /**
     * Checks if this transaction represents a credit (money added)
     *
     * @return true if the transaction adds money to the account
     */
    public boolean isCredit() {
        return type == TransactionType.DEPOSIT ||
               type == TransactionType.TRANSFER_IN ||
               type == TransactionType.INTEREST ||
               type == TransactionType.AUTOMATIC;
    }

    /**
     * Checks if this transaction represents a debit (money removed)
     *
     * @return true if the transaction removes money from the account
     */
    public boolean isDebit() {
        return type == TransactionType.WITHDRAWAL ||
               type == TransactionType.TRANSFER_OUT;
    }
}
