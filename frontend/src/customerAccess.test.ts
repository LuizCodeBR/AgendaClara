import {expect,it} from 'vitest';
import {reservationLink,reservationToken} from './customerAccess';
import {readRoute} from './routing';
const token='a'.repeat(43);
it('aceita segredo e link da mesma instalação sem aceitar endereço externo ou referência pública',()=>{
 expect(reservationToken(token)).toBe(token);expect(reservationToken(reservationLink(token))).toBe(token);
 expect(reservationToken('https://outro.example/#/minha-reserva?token='+token)).toBe('');
 expect(reservationToken('PUBLICO123')).toBe('');
 expect(reservationToken('javascript:alert(1)')).toBe('');
 expect(reservationToken(window.location.origin+'/?token='+token+'#/minha-reserva')).toBe('');
});
it('distingue link privado de identificador público da página de reserva',()=>{
 expect(readRoute('#/minha-reserva?token='+token)).toEqual({mode:'customer',slug:'',token,reservationKey:''});
 expect(readRoute('#/minha-reserva?reserva=11111111-1111-1111-1111-111111111111')).toEqual({mode:'customer',slug:'',token:'',reservationKey:'11111111-1111-1111-1111-111111111111'});
});
