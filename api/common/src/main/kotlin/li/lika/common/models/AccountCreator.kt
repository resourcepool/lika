package li.lika.common.models

data class AccountCreator(var name: String = "", var global: Boolean = false) {
    // This class is used to create a new account with a name and a list of domain IDs.
    // The 'global' flag indicates whether the account is global or not.
}
