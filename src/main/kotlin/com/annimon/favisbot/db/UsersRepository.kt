package com.annimon.favisbot.db

import com.dieselpoint.norm.Database
import com.google.inject.Inject

class UsersRepository @Inject constructor(private val db: Database) {

    fun isUserExists(id: Int): Boolean {
        return db.sql("SELECT COUNT(*) FROM users WHERE id = ?", id)
                .first(Int::class.java) != 0
    }

    fun findUserById(id: Long): DbUser? = db.where("id = ?", id).first(DbUser::class.java)

    fun findUserByGUID(guid: String): DbUser? = db.where("guid = ?", guid).first(DbUser::class.java)

    fun upsertUser(user: DbUser) {
        if (isUserExists(user.id)) {
            db.update(user)
        } else {
            db.insert(user)
        }
    }

    fun findUsersByAllowance(allowance: Int) =
            db.where("allowed = ?", allowance)
                    .results(DbUser::class.java)
}
