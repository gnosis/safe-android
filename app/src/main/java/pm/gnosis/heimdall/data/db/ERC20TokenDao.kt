package pm.gnosis.heimdall.data.db

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import io.reactivex.Flowable
import pm.gnosis.heimdall.data.db.models.ERC20TokenDb
import java.math.BigInteger

@Dao
interface ERC20TokenDao {
    @Insert(onConflict = OnConflictStrategy.FAIL)
    fun insertERC20Token(erC20Token: ERC20TokenDb)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertERC20Tokens(erC20Tokens: List<ERC20TokenDb>)

    @Query("SELECT * FROM ${ERC20TokenDb.TABLE_NAME} ORDER BY ${ERC20TokenDb.COL_NAME} ASC")
    fun observeTokens(): Flowable<List<ERC20TokenDb>>

    @Query("DELETE FROM ${ERC20TokenDb.TABLE_NAME} WHERE ${ERC20TokenDb.COL_ADDRESS} = :address")
    fun deleteToken(address: BigInteger)
}
