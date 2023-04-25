package cc.controlReciclado;
import org.jcsp.lang.*;
import es.upm.aedlib.indexedlist.ArrayIndexedList;



public class ControlRecicladoCSP implements ControlReciclado, CSProcess {

	
	private enum Estado { LISTO, SUSTITUIBLE, SUSTITUYENDO }

	private final int MAX_P_CONTENEDOR;
	private final int MAX_P_GRUA;        
	
	private final Any2OneChannel chNotificarPeso;
	private final Any2OneChannel chIncrementarPeso;
	private final Any2OneChannel chNotificarSoltar;
	private final Any2OneChannel chPrepararSustitucion;
	private final Any2OneChannel chNotificarSustitucion;
	

	
	private static class PetIncrementarPeso {
		public int pesopet;
		public One2OneChannel chACK;

		PetIncrementarPeso (int p) {
			this.pesopet = p;
			this.chACK = Channel.one2one();
		}
	}




	public ControlRecicladoCSP(int max_p_contenedor,
			int max_p_grua) {
		
		MAX_P_CONTENEDOR = max_p_contenedor;
		MAX_P_GRUA       = max_p_grua;
		chNotificarPeso = Channel.any2one();
		chIncrementarPeso = Channel.any2one();
		chNotificarSoltar = Channel.any2one();
		chPrepararSustitucion = Channel.any2one();
		chNotificarSustitucion = Channel.any2one();
		
		new ProcessManager(this).start();
	}

	
	public void notificarPeso(int p) throws IllegalArgumentException {
		
		if(p<=0 || p >MAX_P_GRUA)
			throw new IllegalArgumentException();

		
		chNotificarPeso.out().write(p);
	}

	
	public void incrementarPeso(int p) throws IllegalArgumentException {
		
		if(p<=0 || p > MAX_P_GRUA) {
			throw new IllegalArgumentException();
		}
		
		PetIncrementarPeso pet1 = new PetIncrementarPeso(p);	
		chIncrementarPeso.out().write(pet1);
		pet1.chACK.in().read();
		
	}

	
	public void notificarSoltar() {
		chNotificarSoltar.out().write(null);
	}

	
	public void prepararSustitucion() {  
		
		chPrepararSustitucion.out().write(null);
	}


	public void notificarSustitucion() {
		
		chNotificarSustitucion.out().write(null);
	}

	// SERVIDOR
	
	public void run() {
		
		 Estado estado = Estado.LISTO;
		 int peso = 0;
		 int accediendo = 0;
		
		Guard[] entradas = {
				chNotificarPeso.in(),
				chIncrementarPeso.in(),
				chNotificarSoltar.in(),
				chPrepararSustitucion.in(),
				chNotificarSustitucion.in()
		};
		Alternative servicios =  new Alternative (entradas);
		
		final int NOTIFICAR_PESO = 0;
		final int INCREMENTAR_PESO = 1;
		final int NOTIFICAR_SOLTAR = 2;
		final int PREPARAR_SUSTITUCION = 3;
		final int NOTIFICAR_SUSTITUCION = 4;

		final boolean[] sincCond = new boolean[5];

		sincCond[NOTIFICAR_SOLTAR] = true; 
		sincCond[NOTIFICAR_SUSTITUCION] = true;
		sincCond[INCREMENTAR_PESO] = true;
		
		ArrayIndexedList<PetIncrementarPeso> petAplazadas = new ArrayIndexedList<>();
		
		
		while (true) {

			
			sincCond[NOTIFICAR_PESO] = estado != Estado.SUSTITUYENDO;
			sincCond[PREPARAR_SUSTITUCION] = estado == Estado.SUSTITUIBLE && accediendo == 0;
			
			switch (servicios.fairSelect(sincCond)) {
			case NOTIFICAR_PESO:
				int pesoNot = (Integer) chNotificarPeso.in().read();
				if((peso + pesoNot) > MAX_P_CONTENEDOR) {
					estado = Estado.SUSTITUIBLE;
				}
				else {
					estado = Estado.LISTO;
				}

				
				break;
			case INCREMENTAR_PESO:
				PetIncrementarPeso p = (PetIncrementarPeso)chIncrementarPeso.in().read();
				if(estado != Estado.SUSTITUYENDO && (peso + p.pesopet) <= MAX_P_CONTENEDOR) {
					accediendo++;
					peso = peso + p.pesopet;
					p.chACK.out().write(null);
				}
				else {
					petAplazadas.add(petAplazadas.size(), p);
				}
				break;
			case NOTIFICAR_SOLTAR:
				chNotificarSoltar.in().read();
				accediendo--;
				break;
			case PREPARAR_SUSTITUCION:
				chPrepararSustitucion.in().read();
				estado = Estado.SUSTITUYENDO;
				accediendo = 0;
				break;
			case NOTIFICAR_SUSTITUCION:
				chNotificarSustitucion.in().read();
				peso = 0;
				estado = Estado.LISTO;
				accediendo = 0;
				break;
			}
			ArrayIndexedList<PetIncrementarPeso> aux = new ArrayIndexedList<>(petAplazadas);
			for (PetIncrementarPeso peticion : aux) {
				if(estado != Estado.SUSTITUYENDO && (peso + peticion.pesopet) <= MAX_P_CONTENEDOR) {
					accediendo++;
					peso = peso + peticion.pesopet;
					peticion.chACK.out().write(null);
					petAplazadas.remove(peticion);
				}
			}


		} 
	} 
}

