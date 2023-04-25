package cc.controlReciclado;
import es.upm.babel.cclib.Monitor;
import es.upm.aedlib.indexedlist.IndexedList;
import es.upm.aedlib.indexedlist.ArrayIndexedList;

public final class ControlRecicladoMonitor implements ControlReciclado {
	private enum Estado { LISTO, SUSTITUIBLE, SUSTITUYENDO }

	private final int MAX_P_CONTENEDOR;
	private final int MAX_P_GRUA;
	private Estado estado;
	private int peso;
	private int accediendo;
	private Monitor mutex;
	private Monitor.Cond cond1;
	private Monitor.Cond cond2;
	private IndexedList<Integer> listaPesos = new ArrayIndexedList<>();  
	private IndexedList<Monitor.Cond> listaMonitor = new ArrayIndexedList<>();
	public ControlRecicladoMonitor (int max_p_contenedor,
			int max_p_grua) {
		MAX_P_CONTENEDOR = max_p_contenedor;
		MAX_P_GRUA = max_p_grua;
		estado = Estado.LISTO;
		peso = 0;
		accediendo = 0;
		mutex = new Monitor();
		cond1 = mutex.newCond();
		cond2 = mutex.newCond();
	}

	public void notificarPeso(int p) throws IllegalArgumentException {
		if(p<=0 || p>MAX_P_GRUA) {
			throw new IllegalArgumentException();
		}
		mutex.enter () ;
		if (estado == Estado.SUSTITUYENDO) {
			cond1.await ();
		}
		// si estamos aqui es que se cumple la CPRE
		if((peso + p) > MAX_P_CONTENEDOR) {
			estado = Estado.SUSTITUIBLE;
		}
		if(peso + p <= MAX_P_CONTENEDOR )	 {
			estado = Estado.LISTO;
		}

		// podemos desbloquear a otro proceso?
		ElVeldaderoDesbloqueo ();
		mutex.leave ();  
	}

	public void incrementarPeso(int p) throws IllegalArgumentException{
		if(p<=0 || p > MAX_P_GRUA) {
			throw new IllegalArgumentException();
		}
		mutex.enter ( );

		if ( peso + p > MAX_P_CONTENEDOR || estado == Estado.SUSTITUYENDO) {
			int n = listaPesos.size();
			listaPesos.add(n,p);
			listaMonitor.add(listaMonitor.size(), mutex.newCond());
			listaMonitor.get(listaMonitor.size()-1).await();
		}
		// si estamos aqui es que se cumple la CPRE
		peso = peso + p;
		accediendo= accediendo + 1;

		// podemos desbloquear a otro proceso?
		ElVeldaderoDesbloqueo ();
		mutex.leave ();
	}

	public void notificarSoltar() {
		mutex.enter ( ) ;

		// si estamos aqui es que se cumple la CPRE
		accediendo= accediendo - 1;

		// podemos desbloquear a otro proceso?
		ElVeldaderoDesbloqueo ();
		mutex.leave ();
	}

	public void prepararSustitucion() {
		mutex.enter ( ) ;
		if (estado != Estado.SUSTITUIBLE || accediendo != 0) {
			cond2.await ();
		}
		// si estamos aqui es que se cumple la CPRE
		estado = Estado.SUSTITUYENDO;
		accediendo = 0;

		// podemos desbloquear a otro proceso
		ElVeldaderoDesbloqueo();
		mutex.leave ();
		// opcionalmente, si no es void
	}
	public void notificarSustitucion() {
		mutex.enter();
		estado = Estado.LISTO;
		peso = 0;
		accediendo = 0;
		ElVeldaderoDesbloqueo ();
		mutex.leave();
	}
	public void ElVeldaderoDesbloqueo () {
		boolean aux;
		if ( cond1.waiting() > 0 && estado != Estado.SUSTITUYENDO) {
			cond1.signal ();
		}
		else if (estado == Estado.SUSTITUIBLE && accediendo == 0 && cond2.waiting() > 0) {
			cond2.signal();
		}
		else {
			for(int i = 0; !aux && i < listaMonitor.size() ; i++) {
				if ((peso + listaPesos.get(i)) <= MAX_P_CONTENEDOR && this.estado != Estado.SUSTITUYENDO){
					listaMonitor.get(i).signal();
					listaPesos.removeElementAt(i);
					listaMonitor.removeElementAt(i);
					aux = true;
				}
			}
			
		}
	}
}