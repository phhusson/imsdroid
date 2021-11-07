package org.doubango.test

import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.EditText
import android.content.BroadcastReceiver
import android.content.Context
import org.doubango.ngn.NgnEngine
import org.doubango.ngn.services.INgnConfigurationService
import org.doubango.ngn.services.INgnSipService
import android.os.Bundle
import org.doubango.test.R
import android.content.Intent
import org.doubango.ngn.events.NgnRegistrationEventArgs
import org.doubango.ngn.events.NgnEventArgs
import org.doubango.ngn.events.NgnRegistrationEventTypes
import android.content.IntentFilter
import org.doubango.ngn.utils.NgnConfigurationEntry
import org.doubango.ngn.utils.NgnStringUtils
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.widget.Button
import java.net.InetAddress

class Main : AppCompatActivity() {
    private var mTvInfo: TextView? = null
    private var mEtPublicIdentity: EditText? = null
    private var mEtPrivateIdentity: EditText? = null
    private var mEtPassword: EditText? = null
    private var mEtRealm: EditText? = null
    private var mEtProxyHost: EditText? = null
    private var mEtProxyPort: EditText? = null
    private var mBtSignInOut: Button? = null
    private var mSipBroadCastRecv: BroadcastReceiver? = null
    private val mEngine: NgnEngine
    private val mConfigurationService: INgnConfigurationService
    private val mSipService: INgnSipService
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        mTvInfo = findViewById<View>(R.id.textViewInfo) as TextView
        mEtPublicIdentity = findViewById<View>(R.id.editTextPublicIdentity) as EditText
        mEtPrivateIdentity = findViewById<View>(R.id.editTextPrivateIdentity) as EditText
        mEtPassword = findViewById<View>(R.id.editTextPassword) as EditText
        mEtRealm = findViewById<View>(R.id.editTextRealm) as EditText
        mEtProxyHost = findViewById<View>(R.id.editTextProxyHost) as EditText
        mEtProxyPort = findViewById<View>(R.id.editTextProxyPort) as EditText
        mBtSignInOut = findViewById<View>(R.id.buttonSignInOut) as Button

        val nwr = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .setNetworkSpecifier("1")
                .build()
        val cm = getSystemService(ConnectivityManager::class.java)
        val tm = getSystemService(TelephonyManager::class.java)
        cm.requestNetwork(nwr, object : NetworkCallback() {
            override fun onAvailable(n: Network) {
                cm.bindProcessToNetwork(n)
                Log.d("PHH", "Bound process to IMS")

                val lp = cm.getLinkProperties(n)
                Log.d("PHH-IMS", "got link properties $lp")
                val pcscfServers = lp!!.javaClass.getMethod("getPcscfServers").invoke(lp) as Collection<InetAddress>
                Log.d("PHH-IMS", "got P-CSCF $pcscfServers")
                Log.d("PHH-IMS", "...got P-CSCF ${pcscfServers.first().hostAddress}")

                val imsi = tm.subscriberId
                Log.d("PHH-IMS", "Got IMSI $imsi ${tm.networkOperator} ${tm.networkCountryIso}")

                // https://ptabdata.blob.core.windows.net/files/2017/IPR2017-01509/v13_Ex.%201013%20-%203GGP%20TS%2023.003%20v7.1.0%20%282006-09%29.pdf
                // section 16.2
                // TODO: Correctly split mnc/mcc or ask Android to do it for us
                val mnc = "0" + imsi.substring(3 .. 4)
                val mcc = imsi.substring( 0 .. 2)
                Log.d("PHH-IMS", "mncmcc $mnc $mcc")
                val domain = "ims.mnc$mnc.mcc$mcc.pub.3gppnetwork.org"

                //val name = "0${imsi}@$domain"
                val name = "${imsi}@$domain"

                val myip = lp.linkAddresses[0].address
                Log.d("PHH-IMS", "My IP is ${myip.hostAddress}")

                try {
                    val impi = tm.javaClass.getMethod("getIsimImpi").invoke(tm)
                    val domain = tm.javaClass.getMethod("getIsimDomain").invoke(tm)
                    val impu = tm.javaClass.getMethod("getIsimImpu").invoke(tm)
                    val ist = tm.javaClass.getMethod("getIsimIst").invoke(tm)
                    val pcscf = tm.javaClass.getMethod("getIsimPcscf").invoke(tm)
                    Log.d("PHH-IMS", "Got isim2 $impi $domain $impu $ist $pcscf")
                } catch(e: Exception) {
                    Log.d("PHH-IMS", "isim2 stuff", e)
                }

                runOnUiThread {
                    mEtPublicIdentity!!.text.clear()
                    mEtPublicIdentity!!.text.append("sip:$name")

                    mEtPrivateIdentity!!.text.clear()
                    mEtPrivateIdentity!!.text.append(name)

                    mEtProxyHost!!.text.clear()
                    mEtProxyHost!!.text.append(pcscfServers.first().hostAddress)

                    mEtRealm!!.text.clear()
                    mEtRealm!!.text.append(domain)
                }
            }
        })

        /*

         */

        // Subscribe for registration state changes
        mSipBroadCastRecv = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action

                // Registration Event
                if (NgnRegistrationEventArgs.ACTION_REGISTRATION_EVENT == action) {
                    val args = intent.getParcelableExtra(NgnEventArgs.EXTRA_EMBEDDED) as NgnRegistrationEventArgs?
                    if (args == null) {
                        Log.e(TAG, "Invalid event args")
                        return
                    }
                    when (args.eventType) {
                        NgnRegistrationEventTypes.REGISTRATION_NOK -> mTvInfo!!.text = "Failed to register :("
                        NgnRegistrationEventTypes.UNREGISTRATION_OK -> mTvInfo!!.text = "You are now unregistered :)"
                        NgnRegistrationEventTypes.REGISTRATION_OK -> mTvInfo!!.text = "You are now registered :)"
                        NgnRegistrationEventTypes.REGISTRATION_INPROGRESS -> mTvInfo!!.text = "Trying to register..."
                        NgnRegistrationEventTypes.UNREGISTRATION_INPROGRESS -> mTvInfo!!.text = "Trying to unregister..."
                        NgnRegistrationEventTypes.UNREGISTRATION_NOK -> mTvInfo!!.text = "Failed to unregister :("
                    }
                    mBtSignInOut!!.text = if (mSipService.isRegistered) "Sign Out" else "Sign In"
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(NgnRegistrationEventArgs.ACTION_REGISTRATION_EVENT)
        registerReceiver(mSipBroadCastRecv, intentFilter)

        mBtSignInOut!!.setOnClickListener {
            if (mEngine.isStarted) {
                if (!mSipService.isRegistered) {
                    mConfigurationService.putString(NgnConfigurationEntry.NETWORK_IP_VERSION, "ipv6")

                    // Set credentials
                    mConfigurationService.putString(NgnConfigurationEntry.IDENTITY_IMPI,
                            mEtPrivateIdentity!!.text.toString())
                    mConfigurationService.putString(NgnConfigurationEntry.IDENTITY_IMPU,
                            mEtPublicIdentity!!.text.toString())
                    mConfigurationService.putString(NgnConfigurationEntry.IDENTITY_PASSWORD,
                            mEtPassword!!.text.toString())
                    mConfigurationService.putString(NgnConfigurationEntry.NETWORK_PCSCF_HOST,
                            mEtProxyHost!!.text.toString())
                    mConfigurationService.putInt(NgnConfigurationEntry.NETWORK_PCSCF_PORT,
                            NgnStringUtils.parseInt(mEtProxyPort!!.text.toString(), 5060))
                    mConfigurationService.putString(NgnConfigurationEntry.NETWORK_REALM,
                            mEtRealm!!.text.toString())
                    // VERY IMPORTANT: Commit changes
                    mConfigurationService.commit()
                    // register (log in)
                    mSipService.register(this@Main)
                } else {
                    // unregister (log out)
                    mSipService.unRegister()
                }
            } else {
                mTvInfo!!.text = "Engine not started yet"
            }
        }

    }

    override fun onDestroy() {
        // Stops the engine
        if (mEngine.isStarted) {
            mEngine.stop()
        }
        // release the listener
        if (mSipBroadCastRecv != null) {
            unregisterReceiver(mSipBroadCastRecv)
            mSipBroadCastRecv = null
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Starts the engine
        if (!mEngine.isStarted) {
            if (mEngine.start()) {
                mTvInfo!!.text = "Engine started :)"
            } else {
                mTvInfo!!.text = "Failed to start the engine :("
            }
        }
    }

    companion object {
        private val TAG = Main::class.java.canonicalName
    }

    init {
        mEngine = NgnEngine.getInstance()
        mConfigurationService = mEngine.configurationService
        mSipService = mEngine.sipService
    }
}